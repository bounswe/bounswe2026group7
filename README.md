# BounSWE 2026 - Group 7

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

## Web Application

The web app is a React + Vite frontend backed by a Spring Boot REST API and a
PostgreSQL + Neo4j data layer. Everything below assumes you are at the repo
root.

### Quick start (development, Docker)

Make sure you have [Docker](https://docs.docker.com/get-docker/) and Docker
Compose installed.

```bash
cp .env.example .env
docker compose --profile demo up --build -d
```

That single command does three things:

1. Builds the backend, frontend, and custom Neo4j-GDS images on first run.
2. Starts PostgreSQL, Neo4j, the backend, and the frontend.
3. Once the backend reports healthy, runs the one-shot **`seed`** service —
   it loads the hand-crafted demo dataset (78 users, 288 posts, ~50 mentor
   ratings, 6 active + 48 past mentorships, profile photos, post images,
   task-attached PDFs) into Postgres and into the shared uploads volume.

The seed step is opt-in: omit `--profile demo` if you want a clean DB.

Once everything settles you have four long-running services plus a one-shot
seed container that exits cleanly:

| Service  | URL                                   | Description                                |
|----------|---------------------------------------|--------------------------------------------|
| Frontend | http://localhost:8000                 | React + Vite dev server                    |
| Backend  | http://localhost:8080                 | Spring Boot REST API                       |
| Swagger  | http://localhost:8080/swagger-ui.html | API docs                                   |
| Neo4j    | http://localhost:7474                 | Follow-graph mirror (GDS plugin)           |
| Database | `localhost:5433`                      | PostgreSQL (managed via Flyway migrations) |

Defaults in `.env.example` are good enough for local development — no editing
required. The seed-friendly safety flags (`APP_RATELIMIT_ENABLED=false`,
`APP_EMAIL_ENABLED=false`, `APP_SPAM_ENABLED=false`) and the four advanced
ranker toggles (`MENTOR_ADVANCED_RANKER`, `MENTOR_EXPLANATION_ENABLED`,
`FEED_ADVANCED_RANKER`, `FOLLOW_RANKER=advanced`) are all pre-set so the
demo dataset surfaces the full UI. The only value worth editing for a
real local-with-LLM run is `OPENAI_API_KEY` — see the [env reference](#environment-variable-reference)
below.

Stop the stack:

```bash
docker compose --profile demo down
```

Include `--profile demo` here too — without it the seed container is left
orphaned with a stale network reference and the next `up` will fail. Add
`-v` to also drop the Postgres and Neo4j volumes for a fully clean reset.

Re-seed after manual mutation (stack already running):

```bash
docker compose --profile demo run --rm seed
```

`run --rm` runs the loader as a one-shot and removes the container on exit,
so it never goes stale.

### Seeding options

Two seeders are available depending on what you need.

#### Demo dataset (recommended — automatic, hand-crafted content)

The hand-crafted demo dataset (78 users, 288 posts with images,
6 active + 48 past mentorships with tasks/meetings/messages, 45 ratings,
100 mentor-mentee + mentor-pair messages, AI-generated profile photos,
Unsplash-fetched post images, task-attached PDFs) ships pre-built as a
snapshot under `scripts/seed_snapshot_data/` and loads automatically
through the `demo` compose profile shown in the [Quick start](#quick-start-development-docker)
above:

```bash
docker compose --profile demo up --build -d   # first run on a fresh checkout
docker compose --profile demo down            # tear down (don't drop the profile flag)
docker compose --profile demo run --rm seed   # re-seed against a running stack
```

Each load is idempotent (DELETE-then-COPY against seed-owned rows; the
`admin@group7.com` bootstrap is never touched).

The same snapshot is reachable from the host without involving the
compose service, which is useful when iterating on the snapshot itself:

```bash
./scripts/seed.sh load        # uses scripts/seed_snapshot_data/ by default
./scripts/seed.sh dump        # re-captures current DB state into the same dir
./scripts/seed.sh status      # prints seed-owned counts
./scripts/seed.sh help
```

The first call bootstraps `.venv/` and installs `scripts/requirements-seed.txt`;
subsequent calls reuse it.

#### Lightweight roster (`scripts/seed_local.py`)

If you only need a clean list of accounts (1 admin + 40 mentors + 60
mentees, no posts/mentorships/photos), use the older direct-Postgres
seeder. It's destructive-but-scoped — it wipes every prior
`@seed.local` user first and re-inserts the roster.

```bash
pip install -r scripts/requirements-seed.txt
python3 scripts/seed_local.py
```

The script prints a credentials block to stdout and writes the full
roster to `scripts/seed_local_credentials.txt`.

### Default credentials (after seeding)

Sample accounts from the demo dataset (`--profile demo`, `@seed.test`,
all passwords `Seed1234!`):

| Role             | Email                          | Notes                                                   |
|------------------|--------------------------------|---------------------------------------------------------|
| Demo mentee      | `defne.korkmaz.74@seed.test`   | CS senior, 3 past mentorships, no active mentor — ready for AI-match flow |
| Demo mentor      | `yildiz.demir.78@seed.test`    | Active mentorship with Rıza Yavuz; full task + meeting timeline |
| Senior mentor    | `melis.sezen.2@seed.test`      | Cloud Architecture, active mentee Tolga                 |
| Research mentor  | `dilara.oz.5@seed.test`        | Completed engagement with Defne, 5-star review attached |

The full 78-user roster (20 mentors + 57 mentees + 1 admin) is in
`scripts/seed_snapshot_data/data/users.csv`. Every user has the same
password `Seed1234!`.

If you ran `scripts/seed_local.py` instead, accounts use the
`@seed.local` domain with role-specific passwords (`Admin1234!`,
`Mentor1234!`, `Mentee1234!`); the full list is written to
`scripts/seed_local_credentials.txt`.

### Environment variable reference

All knobs below live in `.env.example` with full inline comments. The
table here is a quick map of what each one controls and what state the
demo expects.

#### Seed-friendliness (`.env.example` defaults to demo-safe values)

| Variable                  | Demo (`.env.example`) | Prod          | What it does                                                                |
|---------------------------|-----------------------|---------------|-----------------------------------------------------------------------------|
| `APP_RATELIMIT_ENABLED`   | `false`               | `true`        | Per-IP/per-user login throttling. 78-account demo needs it off for E2E.     |
| `APP_EMAIL_ENABLED`       | `false`               | `true`        | If on, registration sends through Resend; placeholder API key returns 401.  |
| `APP_SPAM_ENABLED`        | `false`               | `true`        | Form-token + honeypot defences on the register form.                        |

#### Advanced ranker stack (`.env.example` defaults all on)

| Variable                       | Demo  | Default in compose | What it controls                                                          |
|--------------------------------|-------|--------------------|---------------------------------------------------------------------------|
| `MENTOR_ADVANCED_RANKER`       | `true`| `false`            | Swaps the rule-based mentor matcher for the weighted-signal pipeline.     |
| `MENTOR_EXPLANATION_ENABLED`   | `true`| `false`            | LLM-authored "why recommended" sentence on each mentor card.              |
| `MENTOR_EXPLANATION_TIMEOUT_MS`| `15000`| `3000`            | Upper bound on the gpt-4o-mini call. 3000 is too tight under load.        |
| `FEED_ADVANCED_RANKER`         | `true`| `false`            | Advanced For-You pipeline (candidate generation + scoring + MMR).         |
| `FEED_BANDIT_ENABLED`          | `false`| `false`           | Contextual bandit — gated behind impression tracking; stays off.          |
| `FOLLOW_RANKER`                | `advanced`| `legacy`       | Multi-signal follow ranker (PPR + second-hop + …). Needs Neo4j.           |
| `FOLLOW_GRAPH_SYNC_ENABLED`    | `true`| `false`            | Mirrors every PG follow/unfollow into Neo4j after-commit.                 |

#### Keys you (might) need to set manually

| Variable          | When to set                                                                                                |
|-------------------|------------------------------------------------------------------------------------------------------------|
| `OPENAI_API_KEY`  | If you want LLM mentor explanations and the semantic-affinity follow signal. Empty = graceful fallback.    |
| `RESEND_API_KEY`  | Only when `APP_EMAIL_ENABLED=true`. The demo stack doesn't need it.                                        |
| `JWT_SECRET`      | Already set to a base64 dev secret; rotate in `.env.production` for any deploy.                            |
| `NEO4J_PASSWORD`  | `group7pass` locally; **must** be rotated in `.env.production`.                                            |

The OpenAI key flows into both embeddings (`text-embedding-3-small`) and
chat (`gpt-4o-mini`) by default. Override with `OPENAI_CHAT_MODEL` /
`OPENAI_EMBEDDING_MODEL` if needed.

### Quick start (development, without Docker)

Prerequisites:

- Java 21
- Maven
- Node.js 18+
- PostgreSQL running on port 5433 with database `group7db`, user `group7`,
  password `group7pass` (matching `.env.example`)
- Neo4j running on `bolt://localhost:7687` with user `neo4j` / `group7pass`
  (only required when `FOLLOW_GRAPH_SYNC_ENABLED=true`, the default)

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend will be available at http://localhost:8000 and proxies `/api`
requests to the backend at port 8080.

End-to-end tests, including how to enable the `/api/test/**` fixture endpoints
used by Playwright, are documented in [frontend/README.md](frontend/README.md).

### Production deployment

The live demo at [mymentornet.org](https://mymentornet.org) is deployed by
[`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) on every
push to `main`. The workflow SSHes into the DigitalOcean droplet and runs:

```bash
git pull origin main
docker compose --profile demo up --build -d
```

The droplet uses the same `docker-compose.yml` as local dev (bundled
Postgres + Neo4j) with its own `.env` carrying real secrets (`JWT_SECRET`,
`OPENAI_API_KEY`, `RESEND_API_KEY`, `NEO4J_PASSWORD`, …). The `--profile demo`
flag activates the one-shot seed service so every deploy re-syncs the
hand-crafted demo dataset; the seed is scoped to `%@seed.test` users only
and never touches real accounts registered through the UI.

For the demo profile to surface the full UI, the droplet's `.env` should
include the same advanced flags shipped in [`.env.example`](.env.example):

```sh
MENTOR_ADVANCED_RANKER=true
MENTOR_EXPLANATION_ENABLED=true
MENTOR_EXPLANATION_TIMEOUT_MS=15000
FEED_ADVANCED_RANKER=true
FOLLOW_RANKER=advanced
FOLLOW_GRAPH_SYNC_ENABLED=true
```

`APP_RATELIMIT_ENABLED`, `APP_EMAIL_ENABLED`, and `APP_SPAM_ENABLED` can
stay at their prod defaults (`true`) — the seed itself uses direct
Postgres connections, so rate limits and spam defences don't affect it.

For a hardened install that doesn't bundle Postgres in-stack (e.g. against
managed PG), use the separate `docker-compose.prod.yml`:

```bash
cp .env.production.example .env.production
# edit .env.production: set NEO4J_PASSWORD, JWT_SECRET, SPRING_DATASOURCE_URL,
# SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, RESEND_API_KEY,
# APP_BASE_URL, APP_FRONTEND_URL, APP_CORS_ALLOWED_ORIGINS
docker compose --env-file .env.production -f docker-compose.prod.yml up --build -d
```

That compose targets a managed PostgreSQL via `SPRING_DATASOURCE_URL`,
runs Neo4j and the backend in-stack, and binds the frontend to host
port 80 by default (override with `FRONTEND_PORT`). The `seed` service
isn't wired into the prod compose by design — production datasets
shouldn't be reset on every boot.

See [PRODUCTION_CHECKLIST.md](PRODUCTION_CHECKLIST.md) for the broader
deployment checklist (TLS, backups, secret rotation).

---

## Mobile Application

See [mobile-app/README.md](mobile-app/README.md) for full setup instructions.

### Quick start (development)

```bash
cd mobile-app
npm install
cp .env.example .env   # set EXPO_PUBLIC_API_URL
npx expo start
```

### Network configuration

The app communicates with the backend via `EXPO_PUBLIC_API_URL` in `mobile-app/.env`.

| Device | `EXPO_PUBLIC_API_URL` value |
|---|---|
| Android emulator (AVD) | `http://10.0.2.2:8080` |
| Physical device (same Wi-Fi) | `http://<your-machine-ip>:8080` |
| Production | `http://<server-ip-or-domain>:8080` |

Do **not** use `localhost` — it resolves to the device itself, not the host machine.

### Default credentials

| Role | Email | Password |
|---|---|---|
| Admin | admin@group7.com | Admin1234! |
| Mentor | mentor@group7.com | Mentor1234! |
| Mentee | mentee@group7.com | Mentee1234! |
