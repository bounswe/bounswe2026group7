#!/usr/bin/env bash
# Demo data seed/snapshot front-end.
#
# Subcommands:
#   setup            Bootstrap (or repair) the local Python venv at .venv/.
#   dump  [DIR]      Capture current DB + uploads into a portable snapshot.
#   load  [DIR]      Replay a snapshot into the local DB + backend container.
#   status           Show what's in the local DB right now.
#
# Default snapshot directory: scripts/seed_snapshot_data/
#
# This wrapper:
#   - Creates .venv/ on first run and installs scripts/requirements-seed.txt.
#   - Calls the right Python module with the venv's interpreter.
#   - Talks directly to Postgres (port 5433) and the backend container —
#     no HTTP API, no OpenAI, no Unsplash.
#
# Requirements on a fresh machine:
#   - Python 3.10+ on PATH.
#   - Docker + the `bounswe2026group7-{backend,db}-1` containers running
#     (i.e. `docker compose up -d`).
#
# Typical first-time flow on a fresh checkout:
#
#   docker compose up -d
#   ./scripts/seed.sh load                # uses scripts/seed_snapshot_data/
#
# After local mutation, capture the new state for a teammate:
#
#   ./scripts/seed.sh dump                # overwrites the same dir
#
# Snapshot the alternate location:
#
#   ./scripts/seed.sh dump /tmp/snap-2026-05-13
#   ./scripts/seed.sh load /tmp/snap-2026-05-13

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENV_DIR="$REPO_ROOT/.venv"
DEFAULT_SNAPSHOT="$REPO_ROOT/scripts/seed_snapshot_data"

ensure_venv() {
    if [[ ! -x "$VENV_DIR/bin/python" ]]; then
        echo "==> No venv at $VENV_DIR — creating one."
        python3 -m venv "$VENV_DIR"
        "$VENV_DIR/bin/pip" install --upgrade pip --quiet
        echo "==> Installing scripts/requirements-seed.txt …"
        "$VENV_DIR/bin/pip" install --quiet -r "$REPO_ROOT/scripts/requirements-seed.txt"
        echo "==> Done."
    fi
}

check_docker() {
    if ! docker info >/dev/null 2>&1; then
        echo "Error: docker daemon not reachable. Is Docker Desktop running?" >&2
        exit 1
    fi
    if ! docker inspect bounswe2026group7-db-1 >/dev/null 2>&1; then
        echo "Error: Postgres container 'bounswe2026group7-db-1' isn't up." >&2
        echo "       Run: docker compose up -d" >&2
        exit 1
    fi
    if ! docker inspect bounswe2026group7-backend-1 >/dev/null 2>&1; then
        echo "Error: backend container 'bounswe2026group7-backend-1' isn't up." >&2
        echo "       Run: docker compose up -d" >&2
        exit 1
    fi
}

case "${1:-help}" in
    setup)
        ensure_venv
        echo "==> venv ready at $VENV_DIR"
        ;;

    dump)
        ensure_venv
        check_docker
        target="${2:-$DEFAULT_SNAPSHOT}"
        echo "==> dumping current state → $target"
        "$VENV_DIR/bin/python" "$REPO_ROOT/scripts/seed_snapshot.py" dump "$target"
        ;;

    load)
        ensure_venv
        check_docker
        target="${2:-$DEFAULT_SNAPSHOT}"
        if [[ ! -d "$target" ]]; then
            echo "Error: snapshot dir not found: $target" >&2
            exit 1
        fi
        echo "==> loading snapshot from $target"
        "$VENV_DIR/bin/python" "$REPO_ROOT/scripts/seed_snapshot.py" load "$target"
        ;;

    status)
        check_docker
        docker exec bounswe2026group7-db-1 psql -U group7 -d group7db -c "
SELECT 'admins'               AS table, count(*) AS n FROM admins
  WHERE id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'mentors',   count(*) FROM mentors
  WHERE id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'mentees',   count(*) FROM mentees
  WHERE id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'posts',     count(*) FROM feed_posts
  WHERE author_id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
    AND deleted_at IS NULL
UNION ALL SELECT 'comments',  count(*) FROM feed_post_comments
  WHERE post_id IN (SELECT id FROM feed_posts WHERE author_id IN
        (SELECT id FROM users WHERE email LIKE '%@seed.test'))
UNION ALL SELECT 'likes',     count(*) FROM feed_post_likes
  WHERE post_id IN (SELECT id FROM feed_posts WHERE author_id IN
        (SELECT id FROM users WHERE email LIKE '%@seed.test'))
UNION ALL SELECT 'follows',   count(*) FROM follows
  WHERE follower_id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'mentorships (active)', count(*) FROM mentorships
  WHERE status='ACTIVE' AND mentor_id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'mentorships (past)',  count(*) FROM mentorships
  WHERE status<>'ACTIVE' AND mentor_id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'ratings',   count(*) FROM mentor_ratings
  WHERE mentor_id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'attachments', count(*) FROM attachments
  WHERE uploader_id IN (SELECT id FROM users WHERE email LIKE '%@seed.test')
UNION ALL SELECT 'task files', count(*) FROM task_assignment_attachments
  WHERE task_id IN (SELECT id FROM tasks WHERE mentorship_id IN
        (SELECT id FROM mentorships WHERE mentor_id IN
              (SELECT id FROM users WHERE email LIKE '%@seed.test')));
"
        ;;

    help|--help|-h|*)
        sed -n '2,30p' "$0"
        exit 0
        ;;
esac
