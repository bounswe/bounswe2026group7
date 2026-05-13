#!/usr/bin/env python3
"""Capture the current MyMentorNet seed DB state into a portable snapshot
and replay it on a fresh database — without using the backend HTTP API.

Use cases:
- Branch off the current hand-crafted demo state and replay it on CI,
  fresh dev machines, or after a `docker compose down -v`.
- Avoid burning Unsplash quota / OpenAI tokens / mentorship-accept
  HTTP roundtrips every time we wipe the DB.

Mechanics:
- `--dump <dir>` reads every seed-related row out of Postgres (rows
  whose users.email matches `%@seed.test` or `%@seed.local`, plus all
  rows that descend from them via FK chains) and writes them as CSV
  files in FK order. It also copies the backend's `/app/uploads/`
  directory tree (profile photos + post / task attachments) and
  captures the current value of every relevant id sequence so future
  inserts don't collide.
- `--load <dir>` truncates the same set of seed rows from the target
  DB, then COPYs the CSVs back in, restores the upload files into the
  backend container, and bumps the sequences.

The script is self-contained: it talks only to the local Postgres
(via psycopg) and to the backend container (via `docker cp` / `docker
exec` for the upload tree). It never touches HTTP, OpenAI, Unsplash,
thispersondoesnotexist, or any other external service.

Usage:
    python scripts/seed_snapshot.py --dump scripts/seed_snapshot_data/
    # ... later, on a fresh DB:
    python scripts/seed_snapshot.py --load scripts/seed_snapshot_data/

Both modes default to `bounswe2026group7-backend-1` for the backend
container name; override with `--container <name>` if your compose
prefix differs.

Backend env requirements for the loaded state to work end-to-end:
- `APP_RATELIMIT_ENABLED=false` (so the loaded users can sign in
  without tripping rate limits)
- `APP_EMAIL_ENABLED=false` (so the verification flow stays no-op)
- The localhost photo URLs in `users.profile_photo` rely on the
  same Vite proxy / mobile-side host-swap behaviour the original
  seed used; nothing extra is required.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Optional

try:
    import psycopg
except ImportError as exc:  # pragma: no cover
    print("psycopg not installed — run: pip install -r scripts/requirements-seed.txt",
          file=sys.stderr)
    raise


# ── configuration ──────────────────────────────────────────────────────────

SEED_DOMAINS = ("seed.test", "seed.local")

# DSN default: prefer env-driven (so the same script works on the host AND
# inside the compose `seed` service which sets POSTGRES_HOST=db), fall back
# to docker-compose's host-side port mapping.
def _default_dsn() -> str:
    if "DATABASE_URL" in os.environ:
        return os.environ["DATABASE_URL"]
    user = os.environ.get("POSTGRES_USER", "group7")
    password = os.environ.get("POSTGRES_PASSWORD", "group7pass")
    db = os.environ.get("POSTGRES_DB", "group7db")
    host = os.environ.get("POSTGRES_HOST", "localhost")
    port = os.environ.get("POSTGRES_PORT") or os.environ.get("DB_PORT") or (
        "5432" if host != "localhost" else "5433"
    )
    return f"postgresql://{user}:{password}@{host}:{port}/{db}"


DEFAULT_BACKEND_CONTAINER = "bounswe2026group7-backend-1"
BACKEND_UPLOADS_PATH = "/app/uploads"

# Tables to dump, in strict FK-satisfying order. Each entry pairs the
# table name with the WHERE clause that filters to seed-owned rows.
# The clauses are written so they can be evaluated independently of
# load order (we never JOIN forward to tables that don't exist yet
# during load — we reference users/mentors/mentees/posts/etc which
# are loaded earlier in the chain).
TABLES: list[tuple[str, str]] = [
    ("users",
     "email LIKE '%@seed.test' OR email LIKE '%@seed.local'"),
    ("admins",
     "id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentors",
     "id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentees",
     "id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentor_interests",
     "mentor_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentor_preferred_mentee_skills",
     "mentor_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentee_interests",
     "mentee_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentee_skills",
     "mentee_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentor_availability_slots",
     "mentor_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("follows",
     "follower_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("attachments",
     "uploader_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("feed_posts",
     "author_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("feed_post_hashtags",
     "post_id IN (SELECT id FROM feed_posts WHERE author_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("feed_post_attachments",
     "post_id IN (SELECT id FROM feed_posts WHERE author_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("feed_post_comments",
     "post_id IN (SELECT id FROM feed_posts WHERE author_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("feed_post_likes",
     "post_id IN (SELECT id FROM feed_posts WHERE author_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("feed_post_comment_likes",
     "comment_id IN (SELECT c.id FROM feed_post_comments c "
     "JOIN feed_posts p ON p.id = c.post_id "
     "WHERE p.author_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("mentorship_requests",
     "mentor_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("mentorships",
     "mentor_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
    ("conversations",
     "(mentorship_id IN (SELECT id FROM mentorships WHERE mentor_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')))"
     " OR (pair_a_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("conversation_participants",
     "conversation_id IN (SELECT id FROM conversations WHERE "
     "(mentorship_id IN (SELECT id FROM mentorships WHERE mentor_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')))"
     " OR (pair_a_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')))"),
    ("messages",
     "conversation_id IN (SELECT id FROM conversations WHERE "
     "(mentorship_id IN (SELECT id FROM mentorships WHERE mentor_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')))"
     " OR (pair_a_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')))"),
    ("tasks",
     "mentorship_id IN (SELECT id FROM mentorships WHERE mentor_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("task_assignment_attachments",
     "task_id IN (SELECT t.id FROM tasks t JOIN mentorships m ON m.id = t.mentorship_id "
     "WHERE m.mentor_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("milestones",
     "mentorship_id IN (SELECT id FROM mentorships WHERE mentor_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("meetings",
     "mentorship_id IN (SELECT id FROM mentorships WHERE mentor_id IN "
     "(SELECT id FROM users WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local'))"),
    ("mentor_ratings",
     "mentor_id IN (SELECT id FROM users "
     "WHERE email LIKE '%@seed.test' OR email LIKE '%@seed.local')"),
]

# Tables whose id-sequence we want to bump after load. The bare-id
# sequence name follows the `<table>_id_seq` convention; psycopg
# applies setval to the right object name via pg_get_serial_sequence.
ID_SEQ_TABLES = [
    "users", "feed_posts", "feed_post_comments",
    "mentorship_requests", "mentorships", "tasks", "milestones",
    "meetings", "mentor_ratings", "conversations", "messages",
    "mentor_availability_slots",
]
# (attachments uses a UUID PK with no serial sequence — excluded.)


# ── helpers ────────────────────────────────────────────────────────────────

def _truncate_order() -> list[str]:
    """Reverse of insert order, used to TRUNCATE during --load. Children
    first so we never violate FKs while wiping."""
    return list(reversed([t for t, _ in TABLES]))


def _connect(dsn: str) -> psycopg.Connection:
    return psycopg.connect(dsn)


def _count_rows(cur, table: str, where: str) -> int:
    cur.execute(f"SELECT count(*) FROM {table} WHERE {where}")
    return cur.fetchone()[0]


def _capture_table_csv(cur, table: str, where: str, out_path: Path) -> int:
    """Run COPY ... TO STDOUT into a local CSV file. Returns row count."""
    with out_path.open("wb") as fh:
        copy_sql = (
            f"COPY (SELECT * FROM {table} WHERE {where}) "
            "TO STDOUT WITH (FORMAT csv, HEADER true)"
        )
        with cur.copy(copy_sql) as copy:
            for chunk in copy:
                fh.write(chunk)
    return _count_rows(cur, table, where)


def _capture_sequences(cur, tables: Iterable[str]) -> dict[str, int]:
    out: dict[str, int] = {}
    for t in tables:
        cur.execute(
            "SELECT COALESCE((SELECT last_value FROM "
            f"  pg_sequences WHERE schemaname = current_schema() "
            f"   AND sequencename = pg_get_serial_sequence(%s, 'id')::regclass::text "
            "), 0)",
            (t,),
        )
        # The above is over-engineered for portability; fall back to a
        # simpler MAX(id) which is what we actually need to bump to.
        cur.execute(f"SELECT COALESCE(MAX(id), 0) FROM {t}")
        out[t] = int(cur.fetchone()[0])
    return out


def _docker_cp(container: str, src_in_container: str, dest_on_host: Path) -> None:
    dest_on_host.parent.mkdir(parents=True, exist_ok=True)
    if dest_on_host.exists():
        if dest_on_host.is_dir():
            shutil.rmtree(dest_on_host)
        else:
            dest_on_host.unlink()
    subprocess.run(
        ["docker", "cp", f"{container}:{src_in_container}", str(dest_on_host)],
        check=True,
    )


def _docker_cp_into(container: str, src_on_host: Path, dest_in_container: str) -> None:
    subprocess.run(
        ["docker", "cp", str(src_on_host), f"{container}:{dest_in_container}"],
        check=True,
    )


def _docker_exec(container: str, *args: str) -> str:
    result = subprocess.run(
        ["docker", "exec", container, *args],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"docker exec {container} {' '.join(args)} failed: {result.stderr}"
        )
    return result.stdout


# ── dump ──────────────────────────────────────────────────────────────────

def dump(snapshot_dir: Path, dsn: str, container: str) -> None:
    snapshot_dir.mkdir(parents=True, exist_ok=True)
    data_dir = snapshot_dir / "data"
    data_dir.mkdir(exist_ok=True)
    uploads_dir = snapshot_dir / "uploads"
    if uploads_dir.exists():
        shutil.rmtree(uploads_dir)

    manifest: dict = {
        "dumped_at": datetime.now(timezone.utc).isoformat(),
        "source_dsn_host": dsn.split("@", 1)[-1] if "@" in dsn else dsn,
        "container": container,
        "tables": {},
        "sequences": {},
    }

    print(f"==> Dumping rows to {data_dir} …")
    with _connect(dsn) as conn:
        with conn.cursor() as cur:
            for table, where in TABLES:
                csv_path = data_dir / f"{table}.csv"
                n = _capture_table_csv(cur, table, where, csv_path)
                manifest["tables"][table] = n
                print(f"    {table:<32} {n:>6} rows  → {csv_path.name}")
            print("==> Capturing id-sequence high-water marks …")
            manifest["sequences"] = _capture_sequences(cur, ID_SEQ_TABLES)
            for k, v in manifest["sequences"].items():
                print(f"    {k:<32} MAX(id)={v}")

    print(f"==> Copying backend uploads from {container}:{BACKEND_UPLOADS_PATH} …")
    _docker_cp(container, BACKEND_UPLOADS_PATH, uploads_dir)
    photo_count = sum(1 for _ in (uploads_dir / "photos").iterdir()
                      if (uploads_dir / "photos").exists()) if (uploads_dir / "photos").exists() else 0
    att_count = sum(1 for _ in (uploads_dir / "attachments").iterdir()
                    if (uploads_dir / "attachments").exists()) if (uploads_dir / "attachments").exists() else 0
    manifest["uploads"] = {"photos": photo_count, "attachments": att_count}

    (snapshot_dir / "manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True),
        encoding="utf-8",
    )
    print(f"==> Manifest written.")
    print(f"==> Snapshot ready at {snapshot_dir}")


# ── load ──────────────────────────────────────────────────────────────────

def load(snapshot_dir: Path, dsn: str, container: str,
         uploads_mode: str = "docker",
         uploads_dest: Optional[str] = None) -> None:
    """Replay a snapshot into the target DB and restore the uploads tree.

    uploads_mode:
        "docker" — call `docker cp <snapshot/uploads>` into the backend
                   container at /app/uploads. The default when the
                   tool runs from a developer's host.
        "local"  — copy directly into uploads_dest on the local
                   filesystem. Used by the docker-compose `seed` service,
                   which has the backend's uploads volume bind-mounted.
    """
    data_dir = snapshot_dir / "data"
    uploads_dir = snapshot_dir / "uploads"
    manifest_path = snapshot_dir / "manifest.json"

    if not data_dir.is_dir():
        raise SystemExit(f"data/ missing under {snapshot_dir}")
    if not manifest_path.is_file():
        raise SystemExit(f"manifest.json missing under {snapshot_dir}")

    manifest = json.loads(manifest_path.read_text())
    print(f"==> Loading snapshot dumped at {manifest.get('dumped_at')}")

    with _connect(dsn) as conn:
        conn.autocommit = False
        with conn.cursor() as cur:
            print("==> Truncating existing seed rows (child-first order) …")
            for table in _truncate_order():
                csv_path = data_dir / f"{table}.csv"
                if not csv_path.exists():
                    continue
                # Delete using the same WHERE we used when dumping —
                # cleanly idempotent for re-loads.
                where = dict(TABLES)[table]
                cur.execute(f"DELETE FROM {table} WHERE {where}")
                deleted = cur.rowcount or 0
                print(f"    -{table:<32} {deleted:>6} deleted")

            print("==> Copying rows from CSV into Postgres (parent-first order) …")
            for table, _where in TABLES:
                csv_path = data_dir / f"{table}.csv"
                if not csv_path.exists():
                    continue
                with csv_path.open("rb") as fh:
                    header_line = fh.readline().decode("utf-8").rstrip("\n").rstrip("\r")
                    columns = [c.strip() for c in header_line.split(",")]
                    copy_sql = (
                        f"COPY {table} ({', '.join(columns)}) "
                        "FROM STDIN WITH (FORMAT csv, HEADER false)"
                    )
                    with cur.copy(copy_sql) as copy:
                        for chunk in iter(lambda: fh.read(64 * 1024), b""):
                            copy.write(chunk)
                # Count only the seed-owned rows so the display lines up
                # with the manifest (non-seed bootstrap rows are ignored).
                cur.execute(f"SELECT count(*) FROM {table} WHERE {_where}")
                got = cur.fetchone()[0]
                expected = manifest["tables"].get(table)
                marker = "✓" if expected is None or got == expected else "!"
                print(f"    +{table:<32} {got:>6} rows  {marker}")

            print("==> Bumping id sequences …")
            for table in ID_SEQ_TABLES:
                cur.execute(
                    f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), "
                    f"COALESCE((SELECT MAX(id) FROM {table}), 1))"
                )
        conn.commit()

    if uploads_mode == "local":
        dest_root = Path(uploads_dest or BACKEND_UPLOADS_PATH)
        print(f"==> Restoring backend uploads into {dest_root} (local copy) …")
        if uploads_dir.is_dir():
            for sub in ("photos", "attachments"):
                host_sub = uploads_dir / sub
                if not host_sub.is_dir():
                    continue
                target = dest_root / sub
                target.mkdir(parents=True, exist_ok=True)
                n = 0
                for src in host_sub.iterdir():
                    if src.is_file():
                        shutil.copy2(src, target / src.name)
                        n += 1
                print(f"    {sub:<32} {n:>6} files restored")
        else:
            print("    (no uploads/ dir in snapshot — skipping)")
    else:
        print(f"==> Restoring backend uploads into {container}:{BACKEND_UPLOADS_PATH} …")
        if uploads_dir.is_dir():
            # docker cp doesn't merge — replace each subdir wholesale.
            for sub in ("photos", "attachments"):
                host_sub = uploads_dir / sub
                if not host_sub.is_dir():
                    continue
                target = f"{BACKEND_UPLOADS_PATH}/{sub}"
                # ensure target dir exists; orphan files from prior runs are
                # left in place because the backend may run as a non-root user
                # that can't delete files owned by another uid. Orphans are
                # harmless — nothing in the DB references them.
                _docker_exec(container, "mkdir", "-p", target)
                _docker_cp_into(container, host_sub, BACKEND_UPLOADS_PATH)
                n = len(list(host_sub.iterdir()))
                print(f"    {sub:<32} {n:>6} files restored")
        else:
            print("    (no uploads/ dir in snapshot — skipping)")

    print("==> Load complete.")


# ── cli ───────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="mode", required=True)

    pdump = sub.add_parser("dump", help="Capture current state into a snapshot dir")
    pdump.add_argument("snapshot_dir", help="Output directory")
    pdump.add_argument("--dsn", default=_default_dsn())
    pdump.add_argument("--container", default=DEFAULT_BACKEND_CONTAINER)

    pload = sub.add_parser("load", help="Replay a snapshot dir into the local DB")
    pload.add_argument("snapshot_dir", help="Input directory (created by --dump)")
    pload.add_argument("--dsn", default=_default_dsn())
    pload.add_argument("--container", default=DEFAULT_BACKEND_CONTAINER)
    pload.add_argument("--uploads-mode", choices=("docker", "local"),
                       default="docker",
                       help="docker: docker cp into the backend container; "
                            "local: shutil.copy into --uploads-dest (used "
                            "by the docker-compose `seed` profile which has "
                            "the uploads volume bind-mounted).")
    pload.add_argument("--uploads-dest", default=BACKEND_UPLOADS_PATH,
                       help="Destination dir for --uploads-mode=local")

    args = parser.parse_args()
    if args.mode == "dump":
        dump(Path(args.snapshot_dir), args.dsn, args.container)
    elif args.mode == "load":
        load(Path(args.snapshot_dir), args.dsn, args.container,
             uploads_mode=args.uploads_mode,
             uploads_dest=args.uploads_dest)
    return 0


if __name__ == "__main__":
    sys.exit(main())
