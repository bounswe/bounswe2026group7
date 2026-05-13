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
docker compose up --build
```

This starts four services:

| Service  | URL                                   | Description                                |
|----------|---------------------------------------|--------------------------------------------|
| Frontend | http://localhost:8000                 | React + Vite dev server                    |
| Backend  | http://localhost:8080                 | Spring Boot REST API                       |
| Swagger  | http://localhost:8080/swagger-ui.html | API docs                                   |
| Neo4j    | http://localhost:7474                 | Follow-graph mirror (GDS plugin)           |
| Database | `localhost:5433`                      | PostgreSQL (managed via Flyway migrations) |

Defaults in `.env.example` are good enough for local development — no editing
required. The seed-friendly safety flags (`APP_RATELIMIT_ENABLED=false`,
`APP_EMAIL_ENABLED=false`, `APP_SPAM_ENABLED=false`) and the three advanced
ranker toggles (`MENTOR_ADVANCED_RANKER`, `MENTOR_EXPLANATION_ENABLED`,
`FEED_ADVANCED_RANKER`, `FOLLOW_RANKER=advanced`) are all pre-set so the
demo dataset surfaces the full UI. The only value worth editing for a
real local-with-LLM run is `OPENAI_API_KEY` — see the [env reference](#environment-variable-reference)
below.

Stop the stack with `docker compose down`. Add `-v` to also drop the Postgres
and Neo4j volumes if you want a fully clean reset on the next boot.

### Seed local data

Right after a fresh `docker compose up`, the app is empty. Two seeders are
available depending on what you need.

#### Demo dataset (recommended — automatic, hand-crafted content)

The richer demo dataset (78 users, 288 posts with images, 6 active +
48 past mentorships with tasks/meetings/messages, 45 ratings, 100
mentor-mentee + mentor-pair messages, AI-generated profile photos,
Unsplash-fetched post images) ships pre-built as a snapshot under
`scripts/seed_snapshot_data/` and loads automatically through a
docker-compose profile:

```bash
docker compose --profile demo up -d
```

Adding the `--profile demo` flag enables a one-shot `seed` service that
waits for the backend to be healthy, COPYs the snapshot into Postgres,
and restores the upload binaries into the shared volume. The service
exits when it's done; the rest of the stack keeps running. Re-running
with `--profile demo` is idempotent (DELETE-then-COPY).

If you've already started the stack without the profile, you can load
the demo dataset after the fact:

```bash
docker compose --profile demo up seed
```

You can also run the loader from the host (useful when iterating on the
snapshot itself):

```bash
./scripts/seed.sh load
```

The host-side wrapper bootstraps `.venv/` on first run and pre-flights
docker. Other subcommands: `./scripts/seed.sh dump` to re-capture the
current DB state, `./scripts/seed.sh status` to inspect counts.

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

Production uses a separate compose file (`docker-compose.prod.yml`) that
expects a managed Postgres and reads secrets from `.env.production`.

```bash
cp .env.production.example .env.production
# edit .env.production: set NEO4J_PASSWORD, JWT_SECRET, SPRING_DATASOURCE_URL,
# SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, RESEND_API_KEY,
# APP_BASE_URL, APP_FRONTEND_URL, APP_CORS_ALLOWED_ORIGINS
docker compose --env-file .env.production -f docker-compose.prod.yml up --build -d
```

The prod compose runs the backend, frontend, and Neo4j in-stack and binds the
frontend to host port 80 by default (override with `FRONTEND_PORT`). It does
not bring up a Postgres service — point `SPRING_DATASOURCE_URL` at your
managed database.

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
