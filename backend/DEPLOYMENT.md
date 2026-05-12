# Backend Deployment Guide

This document is the operator-facing reference for running the BounSWE 2026 Group 7 backend in any environment. It covers local development, staging, and production deployment on a DigitalOcean droplet, plus the operational tasks you'll need afterwards (database, secrets, upgrades, troubleshooting).

For contributor-level "how do I run the app to develop a feature" instructions, the root [`README.md`](../README.md) is shorter and gets you to a working stack in two commands. This file picks up where that one stops.

---

## Contents

1. [What you are deploying](#what-you-are-deploying)
2. [Prerequisites](#prerequisites)
3. [Local development (Docker Compose)](#local-development-docker-compose)
4. [Running the backend without Docker](#running-the-backend-without-docker)
5. [Production deployment (DigitalOcean droplet)](#production-deployment-digitalocean-droplet)
6. [Environment variables reference](#environment-variables-reference)
7. [Database — Flyway, backups, restore](#database--flyway-backups-restore)
8. [Neo4j follow-graph (optional)](#neo4j-follow-graph-optional)
9. [Health checks and observability](#health-checks-and-observability)
10. [Updates and rollouts](#updates-and-rollouts)
11. [Operational runbook](#operational-runbook)
12. [Troubleshooting](#troubleshooting)
13. [Pre-production checklist](#pre-production-checklist)

---

## What you are deploying

The backend is a **Spring Boot 3.5 / Java 21** REST + WebSocket service that owns:

- User identity (register / verify-email / login / password-reset)
- Mentor + mentee profiles, mentorship requests, matching
- Meetings, milestones, tasks, action items
- Social feed (posts, interactions, follow graph, real-time fanout, image attachments)
- Notifications (in-app + FCM push), reminders, ban + spam-bot defences
- Admin moderation + reporting

Persistence is **PostgreSQL 16** (managed in production, container locally). Schema lives in `backend/src/main/resources/db/migration` and is owned by **Flyway**.

A second datastore — **Neo4j 5 Community + Graph Data Science 2.13.4** — is wired in as an *optional* follow-graph mirror for the advanced follow-recommendation pipeline (#437 / PR #470). Personalised PageRank runs in Neo4j on a graph projected from the PostgreSQL `follows` table. The driver bean is lazy and the entire sync surface is gated behind a feature flag (`app.recommendations.follow.sync.enabled`) — the backend boots cleanly whether or not Neo4j is reachable. See [Neo4j follow-graph (optional)](#neo4j-follow-graph-optional) for when and how to turn it on.

The image is a multi-stage Docker build (`backend/Dockerfile`) — `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`, runs as non-root user `appuser` (uid 10001), exposes port 8080.

Production deployment runs the same image via `docker-compose.prod.yml`, behind a reverse proxy that terminates TLS.

---

## Prerequisites

| Tool | Min version | Used for |
|---|---|---|
| Docker | 24+ | Build + run containers |
| Docker Compose | v2 (`docker compose`) | Multi-service orchestration |
| Java | 21 (Temurin) | Local non-Docker dev only |
| Maven | 3.9 | Local non-Docker dev only |
| `git` | 2.x | Cloning + pulling on the deploy host |
| `openssl` | any | Generating JWT secrets |
| `curl` + `jq` | any | Health checks + scripting |

On the production host you only need Docker, Docker Compose, and `git`. **Neo4j is optional** — only required if you intend to enable the advanced follow-recommendation pipeline (see section 8).

---

## Local development (Docker Compose)

This boots the full stack — DB, backend, frontend, MailHog — with one command. Defaults work without touching env vars.

```bash
git clone https://github.com/bounswe/bounswe2026group7.git
cd bounswe2026group7
cp .env.example .env
docker compose up --build
```

Reachable endpoints:

| Service | URL | Purpose |
|---|---|---|
| Backend API | http://localhost:8080 | Spring Boot |
| Swagger UI | http://localhost:8080/swagger-ui.html | Live OpenAPI docs |
| Health | http://localhost:8080/actuator/health | Liveness probe |
| Frontend | http://localhost:5174 | React + Vite |
| MailHog UI | http://localhost:8025 | Local email inbox (dev) |
| PostgreSQL | localhost:5433 | DB (`group7`/`group7pass` by default) |

**Stopping:**

```bash
docker compose down       # keeps DB volume
docker compose down -v    # also drops db_data + uploads_data
```

**Backend-only iteration** (rebuild just the backend after a code change without touching frontend/db):

```bash
docker compose up -d --build backend
docker compose logs -f backend
```

If you want to skip the Docker frontend and run Vite locally (`npm run dev` on port 5173), set `APP_CORS_ALLOWED_ORIGINS=http://localhost:5173` in `.env` so the backend trusts that origin.

---

## Running the backend without Docker

Use this when you want to attach a debugger, iterate on a hot-reload, or run a single Maven goal.

### 1. Start a Postgres on `localhost:5433`

The simplest path is to keep using the compose `db` service while running the JVM on the host:

```bash
docker compose up -d db
```

Or run a one-off container:

```bash
docker run -d --name pg \
  -e POSTGRES_DB=group7db \
  -e POSTGRES_USER=group7 \
  -e POSTGRES_PASSWORD=group7pass \
  -p 5433:5432 \
  postgres:16-alpine
```

### 2. Run the JVM

```bash
cd backend
mvn spring-boot:run
```

Or run the packaged jar after a build:

```bash
mvn -DskipTests package
java -jar target/backend-*.jar
```

Override settings via environment variables (Spring picks them up automatically — see the [reference table](#environment-variables-reference)):

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/group7db \
SPRING_DATASOURCE_USERNAME=group7 \
SPRING_DATASOURCE_PASSWORD=group7pass \
JWT_SECRET=$(openssl rand -base64 32) \
APP_BASE_URL=http://localhost:8080 \
java -jar target/backend-*.jar
```

### 3. Run the test suite

The integration tests need a live Postgres on `localhost:5433`:

```bash
docker compose up -d db
cd backend
mvn --batch-mode verify
```

JaCoCo coverage HTML lands at `backend/target/site/jacoco/index.html`.

---

## Production deployment (DigitalOcean droplet)

The production setup uses `docker-compose.prod.yml` (no DB service — points at DigitalOcean Managed PostgreSQL) plus the GitHub Actions workflow at `.github/workflows/deploy.yml` for automatic rollouts on push to `main`.

### One-time droplet setup

#### 1. Install Docker on the droplet

```bash
ssh root@<droplet-ip>
curl -fsSL https://get.docker.com | sh
docker --version
docker compose version
```

#### 2. Provision the managed database

In the DigitalOcean control panel:

1. Create a **Managed Database → PostgreSQL 16**, smallest tier that fits your data set.
2. Restrict inbound access to the droplet's VPC IP only (Trusted Sources → add droplet).
3. Note the connection URI; it has the shape `postgresql://<user>:<pass>@<host>:25060/<db>?sslmode=require`.
4. Convert to a JDBC URL for `SPRING_DATASOURCE_URL`:
   `jdbc:postgresql://<host>:25060/<db>?sslmode=require`.

SSL is enforced server-side — `application-prod.properties` sets `spring.datasource.hikari.data-source-properties.sslmode=require` so the JDBC driver wires it through automatically.

#### 3. Clone the repository and create `.env`

```bash
ssh root@<droplet-ip>
cd /root
git clone https://github.com/bounswe/bounswe2026group7.git
cd bounswe2026group7
```

Create `.env` (this file is `.gitignore`d) with **all production values**. A minimal complete file:

```bash
# Database (managed Postgres)
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:25060/<db>?sslmode=require
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>

# JWT — generate fresh, never commit
JWT_SECRET=$(openssl rand -base64 32)
JWT_EXPIRATION=86400000

# Public URLs (must be HTTPS in production)
APP_BASE_URL=https://api.example.com
APP_FRONTEND_URL=https://app.example.com
APP_CORS_ALLOWED_ORIGINS=https://app.example.com

# Email (Resend)
RESEND_API_KEY=re_<live-key>
APP_MAIL_FROM=noreply@example.com
VERIFICATION_TOKEN_EXPIRY_HOURS=24
VERIFICATION_RESEND_MAX_PER_HOUR=3
PASSWORD_RESET_TOKEN_EXPIRY_HOURS=1
PASSWORD_RESET_MAX_REQUESTS_PER_HOUR=5

# Rate limiter (set proxy hops below to what's actually in front of you)
APP_RATELIMIT_ENABLED=true
APP_RATELIMIT_TRUST_XFF=true
APP_RATELIMIT_TRUSTED_PROXIES=1

# Optional: advanced rankers (off until you have an OpenAI key set)
OPENAI_API_KEY=
MENTOR_ADVANCED_RANKER=false
FEED_ADVANCED_RANKER=false

# Frontend image bake-time variable (used by `frontend` service)
VITE_BACKEND_URL=https://api.example.com
```

Lock the file down:

```bash
chmod 600 .env
chown root:root .env
```

#### 4. First boot

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build
docker compose -f docker-compose.prod.yml logs -f backend
```

Wait until you see `Started BackendApplication in <n>s` and the health check turns `UP`. Flyway will apply migrations against the managed database on first boot.

Smoke test:

```bash
curl -fsS http://localhost:8080/actuator/health
# {"status":"UP","groups":["liveness","readiness"]}
```

#### 5. Front the backend with TLS

The droplet should not expose port 8080 publicly. Put nginx (or Caddy, Traefik, etc.) in front to terminate TLS and forward to the container. A minimal nginx site:

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    ssl_certificate     /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;

    # Single reverse-proxy hop → APP_RATELIMIT_TRUSTED_PROXIES=1 in .env
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket upgrade for /ws/chat
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade    $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host       $host;
        proxy_read_timeout 86400;
    }
}
```

Issue the certificate with `certbot --nginx -d api.example.com`. Redirect port 80 → 443 in a second `server` block.

If you add a CDN or WAF in front of nginx (Cloudflare, CloudFront), bump `APP_RATELIMIT_TRUSTED_PROXIES` to the **total number of hops** so the rate limiter reads the right IP from `X-Forwarded-For`. See [`PRODUCTION_CHECKLIST.md`](../PRODUCTION_CHECKLIST.md) section 6 for the full table.

#### 6. Wire automated deploys

`.github/workflows/deploy.yml` SSHes into the droplet on every push to `main` and runs `git pull && docker compose up --build -d`.

Add these repository secrets in GitHub → Settings → Secrets:

| Secret | Value |
|---|---|
| `DEPLOY_HOST` | Droplet IP or DNS |
| `DEPLOY_SSH_KEY` | Private SSH key whose public half is in `/root/.ssh/authorized_keys` on the droplet |

After the first push to `main` the action runs; subsequent pushes ship automatically.

> **Note**: the existing workflow uses `docker-compose.yml` (the dev compose with embedded Postgres) on the deploy step. For production with managed Postgres you want it to use `docker-compose.prod.yml` — change `docker compose up --build -d` to `docker compose -f docker-compose.prod.yml --env-file .env up --build -d` in `.github/workflows/deploy.yml` once the prod compose is your runtime.

---

## Environment variables reference

All values can be set in `.env` (Docker) or exported in the shell (bare JVM). Defaults in parentheses are baked into `application.properties` — production should set every starred row explicitly.

### Datastore

| Var | Default | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` ★ | `jdbc:postgresql://localhost:5433/group7db` | Managed PG URL with `?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` ★ | `group7` | DB user |
| `SPRING_DATASOURCE_PASSWORD` ★ | `group7pass` | DB password |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `prod` in production to activate `application-prod.properties` |

### Security & auth

| Var | Default | Notes |
|---|---|---|
| `JWT_SECRET` ★ | dev placeholder | **Regenerate per environment.** Base64 256-bit string. `openssl rand -base64 32` |
| `JWT_EXPIRATION` | `86400000` (1 day, ms) | Session token TTL |

### URLs and CORS

| Var | Default | Notes |
|---|---|---|
| `APP_BASE_URL` ★ | `http://localhost:8080` | Used in verification + reset email links and `AttachmentUrlBuilder` |
| `APP_FRONTEND_URL` ★ | `http://localhost:5173` | Used in password-reset email links |
| `APP_CORS_ALLOWED_ORIGINS` ★ | `http://localhost:5173,http://localhost:5174` | Comma-separated list; one entry per allowed frontend origin |

### Email

| Var | Default | Notes |
|---|---|---|
| `RESEND_API_KEY` ★ | `re_test_placeholder` | Resend SaaS API key. Live key only in production. |
| `APP_MAIL_FROM` | `noreply@group7.com` | From-address shown to recipients |
| `APP_EMAIL_ENABLED` | `true` | Set `false` to short-circuit to `NoOpEmailService` (used by E2E fixtures) |
| `VERIFICATION_TOKEN_EXPIRY_HOURS` | `24` | Verify-email token TTL |
| `VERIFICATION_RESEND_MAX_PER_HOUR` | `3` | Per-user resend cap |
| `PASSWORD_RESET_TOKEN_EXPIRY_HOURS` | `1` | Reset token TTL |
| `PASSWORD_RESET_MAX_REQUESTS_PER_HOUR` | `5` | Per-user reset cap |

### Uploads

| Var | Default | Notes |
|---|---|---|
| `UPLOAD_DIR` | `/app/uploads/photos` | Profile photo directory |
| `UPLOAD_ATTACHMENTS_DIR` | `/app/uploads/attachments` | Chat + feed attachment directory |
| `UPLOAD_MAX_SIZE` | `5MB` / `6MB` | Servlet multipart caps |
| `UPLOAD_MAX_SIZE_BYTES` | `5242880` | App-level size cap (per file) |
| `UPLOAD_MAX_PER_USER_PER_HOUR` | `30` | Per-user hourly upload quota |
| `UPLOAD_ORPHAN_RETENTION_HOURS` | `24` | How long unreferenced uploads survive before nightly sweep |

Mount `/app/uploads` to a host-side volume that survives restarts (`uploads_data` named volume in compose).

### Rate limiting

| Var | Default | Notes |
|---|---|---|
| `APP_RATELIMIT_ENABLED` | `true` | Turn off for E2E only |
| `APP_RATELIMIT_TRUST_XFF` | `false` | `true` behind reverse proxies |
| `APP_RATELIMIT_TRUSTED_PROXIES` | `1` | Exact hop count (`nginx` only → 1; `CDN+LB` → 2; etc.) |
| `APP_FEED_POST_CAPACITY` | `30` | Per-user hourly cap for `POST /api/feed/posts` |
| `APP_FEED_INTERACTION_CAPACITY` | `60` | Per-user per-minute cap for like / bookmark / share / comment |
| `APP_FEED_UNREAD_CAP` | `99` | Returned ceiling for `GET /api/feed/unread-count` |

> Misconfiguring `APP_RATELIMIT_TRUST_XFF` / `APP_RATELIMIT_TRUSTED_PROXIES` lets attackers spoof `X-Forwarded-For` and pick the rate-limit key. Verify by sending a request through the proxy and confirming the masked IP in backend logs (`****<last4>`) matches your real public IP, not the proxy's.

### Admin bootstrap

| Var | Default | Notes |
|---|---|---|
| `APP_ADMIN_BOOTSTRAP_ENABLED` | `false` | Set `true` once, on a fresh DB, to seed the first admin |
| `APP_ADMIN_BOOTSTRAP_EMAIL` | (empty) | Admin email |
| `APP_ADMIN_BOOTSTRAP_PASSWORD` | (empty) | Admin password — change immediately after first login |
| `APP_ADMIN_BOOTSTRAP_FIRST_NAME` | `System` | Admin first name |
| `APP_ADMIN_BOOTSTRAP_LAST_NAME` | `Admin` | Admin last name |

After the first admin exists, **set `APP_ADMIN_BOOTSTRAP_ENABLED=false`** and remove the password from `.env`.

### Optional: advanced rankers

These are **off by default**; flip on once you have an OpenAI key and want to run the embedding-based mentor / for-you rankers.

| Var | Default | Notes |
|---|---|---|
| `OPENAI_API_KEY` | placeholder | Required for any advanced path |
| `MENTOR_ADVANCED_RANKER` | `false` | Enables semantic mentor ranker |
| `MENTOR_EXPLANATION_ENABLED` | `false` | Enables LLM-generated match explanations |
| `MENTOR_EXPLANATION_TIMEOUT_MS` | `3000` | Per-call timeout |
| `OPENAI_CHAT_MODEL` | `gpt-4o-mini` | Chat model for explanations |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model |
| `FEED_ADVANCED_RANKER` | `false` | Enables MMR + bandit For-You pipeline |
| `FEED_BANDIT_ENABLED` | `false` | Bandit exploration slot on page 0 |

### Neo4j follow-graph (only when enabled)

All of these are read at boot but the surface only fires when `app.recommendations.follow.sync.enabled=true`. Defaults below are conservative — flip the flag on once Neo4j is reachable and GDS is installed.

| Var | Default | Notes |
|---|---|---|
| `SPRING_NEO4J_URI` | (none — auto-config) | Bolt URL, e.g. `bolt://neo4j:7687` or `neo4j+s://<id>.databases.neo4j.io` |
| `SPRING_NEO4J_AUTHENTICATION_USERNAME` | `neo4j` | Neo4j user |
| `SPRING_NEO4J_AUTHENTICATION_PASSWORD` ★ | (none) | **Required** when sync is on; managed instances reject anonymous |
| `APP_RECOMMENDATIONS_FOLLOW_SYNC_ENABLED` | `false` | Master switch for the mirror, AFTER_COMMIT listener, resync cron, and PageRank query path |
| `APP_RECOMMENDATIONS_FOLLOW_RESYNC_CRON` | `0 0 3 * * *` | Daily 03:00 UTC full reconciliation between PG `follows` and Neo4j |
| `APP_RECOMMENDATIONS_FOLLOW_GRAPH_WEIGHT` | `2` | Weight given to PageRank score when blended into the follow-recommendation ranker |

The Bolt driver bean is lazy — instantiation does **not** open a connection. Until something actually queries Neo4j (via the sync listener, resync job, or recommendation endpoint) the backend has no opinion about whether Neo4j is up. This is why the flag can stay `false` in environments where you do not run Neo4j at all.

### Testing helpers (production must keep `false`)

| Var | Default | Notes |
|---|---|---|
| `APP_TEST_ENDPOINTS_ENABLED` | `false` | Exposes `/api/test/*` seed-and-reset helpers. **Force-pinned `false` in `docker-compose.prod.yml`** regardless of host env |
| `APP_SPAM_ENABLED` | `true` | Layered spam-bot defences on register. CI flips off for Playwright runs only |

---

## Database — Flyway, backups, restore

### Schema management

Schema is owned by **Flyway**. Migrations live in `backend/src/main/resources/db/migration/V<n>__<description>.sql`. On every boot Flyway:

1. Connects to the DB.
2. Reads `flyway_schema_history`.
3. Applies any new `V<n>__*.sql` files in version order.
4. Aborts boot if a migration is missing, out of order (`out-of-order=false`), or fails checksum.

**Do not** edit a migration that has already been applied in any environment. Add a new migration on top.

To allocate a new version number: pick `max(version already applied across all environments) + 1`. The "reserved slots in the planning doc" scheme broke once PRs landed out of the planned order — always check the actual applied max.

### Inspecting migrations

```bash
# Local (containerised DB)
docker exec -it bounswe2026group7-db-1 psql -U group7 -d group7db \
  -c "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"

# Production (managed Postgres)
psql "$SPRING_DATASOURCE_URL_NO_JDBC" \
  -c "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"
```

### Backups (managed Postgres)

DigitalOcean Managed PostgreSQL takes automatic daily backups with **7-day retention** on the smallest tier. Verify in the control panel → Backups tab.

For larger retention or off-DO copies, schedule a nightly `pg_dump` to S3 / Spaces:

```bash
pg_dump --no-owner --no-acl --format=custom "$DATABASE_URL" \
  | gzip > "backup-$(date -u +%Y%m%dT%H%M%SZ).dump.gz"
aws s3 cp "backup-*.dump.gz" s3://my-backups/postgres/
```

Cron it on a separate small droplet (not the app droplet) so a host failure does not take both the app and the backup process down.

### Restore

From a managed PG snapshot: use the DO control panel's "Restore from backup" — it creates a new DB cluster from the snapshot. Point `SPRING_DATASOURCE_URL` at the new cluster and redeploy.

From a `pg_dump --format=custom`:

```bash
pg_restore --clean --no-owner --no-acl \
  --dbname "$DATABASE_URL" backup-2026-05-12T03-00-00Z.dump
```

### Wiping local state

Drops everything including the volume. Useful when a half-failed migration leaves a corrupt schema:

```bash
docker compose down -v
docker compose up -d db
```

For just the schema without dropping the volume:

```bash
docker exec -it bounswe2026group7-db-1 psql -U group7 -d group7db \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT ALL ON SCHEMA public TO group7;"
docker compose restart backend
```

---

## Neo4j follow-graph (optional)

The advanced follow-recommendation pipeline (#437, shipped in PR #470) mirrors the PostgreSQL `follows` table into a Neo4j graph and runs **Personalised PageRank** via the **Graph Data Science (GDS)** plugin to score candidate authors by their position in the follower-of-follower graph. The blended ranker combines that PageRank with interest overlap and engagement signals.

This entire surface is **off by default**. The Bolt driver bean is lazy, every sync / query path is gated by `@ConditionalOnProperty(app.recommendations.follow.sync.enabled)`, and the recommendation ranker gracefully falls back to interest-only scoring when the flag is `false`. You can run BounSWE2026 in production indefinitely without ever standing up Neo4j.

When you *do* turn it on, you need three things: a reachable Neo4j 5 instance, the GDS 2.13.4 plugin installed, and the four `APP_RECOMMENDATIONS_FOLLOW_*` / `SPRING_NEO4J_*` env vars set.

### What gets stored

A single label and a single relationship:

```cypher
(:User {id: 123})-[:FOLLOWS {createdAt: <timestamp>}]->(:User {id: 456})
```

`User.id` is the PostgreSQL primary key — the two stores share an integer keyspace. Profile data is **not** mirrored; the graph only needs ids and edges.

### Lifecycle

1. **Bootstrap on first enable**: `FollowGraphBootstrap` runs at startup and walks the PG `follows` table once, projecting every existing edge into Neo4j.
2. **Real-time sync**: every `POST /api/users/{id}/follow` and `DELETE` publishes a `FollowChangedEvent`. `FollowGraphSyncListener` consumes it AFTER_COMMIT and writes the edge into Neo4j.
3. **Failure resilience**: if Neo4j is unreachable when the listener fires, the change is persisted to the `failed_graph_syncs` PG table and retried by `FollowGraphResyncJob`.
4. **Nightly reconciliation**: `FollowGraphResyncJob` runs at `APP_RECOMMENDATIONS_FOLLOW_RESYNC_CRON` (default 03:00 UTC) and replays everything in `failed_graph_syncs` plus a full set-difference between PG and Neo4j to catch any drift.
5. **PageRank computation**: when a user hits `GET /api/users/me/follow-recommendations`, `PersonalizedPageRankService` runs `gds.pageRank.stream` on an in-memory projected graph, blends the score with interest-overlap signals (weight controlled by `APP_RECOMMENDATIONS_FOLLOW_GRAPH_WEIGHT`), and returns the top-N.

### Option A — managed Neo4j Aura (recommended for production)

[Neo4j Aura](https://neo4j.com/cloud/aura/) is the SaaS offering and the simplest production path. The **Free tier** caps at 200k nodes / 400k relationships, which is plenty for a class-sized graph; the smallest paid tier handles millions.

1. Create an Aura DB → AuraDB Professional (or Free).
2. Note the connection URI (looks like `neo4j+s://<id>.databases.neo4j.io`) and the generated password.
3. **Verify GDS availability** — Aura Professional includes GDS out of the box. Aura Free does **not** ship GDS; on Free you can run the sync (mirror) but not the PageRank scoring, so the ranker degrades to interest-only. For a real production deploy with the advanced ranker active, use Aura Professional or self-host.
4. Set the env vars in `.env`:

   ```bash
   SPRING_NEO4J_URI=neo4j+s://<id>.databases.neo4j.io
   SPRING_NEO4J_AUTHENTICATION_USERNAME=neo4j
   SPRING_NEO4J_AUTHENTICATION_PASSWORD=<aura-password>
   APP_RECOMMENDATIONS_FOLLOW_SYNC_ENABLED=true
   ```

5. Redeploy the backend. The bootstrap job runs once and emits a log line like `Follow-graph bootstrap projected N edges in M ms`.

### Option B — self-host on the droplet via Docker

Add a `neo4j` service to your compose file using the **custom GDS image** that already lives in this repo at `backend/docker/neo4j-gds/Dockerfile`. The image bakes the GDS 2.13.4 jar into a Neo4j 5 community layer with a pinned SHA256 — no runtime plugin download, no class-of-2026 supply-chain surprise.

Append to `docker-compose.prod.yml`:

```yaml
services:
  # ... existing backend, frontend ...

  neo4j:
    build: ./backend/docker/neo4j-gds
    environment:
      NEO4J_AUTH: "neo4j/${NEO4J_PASSWORD}"
      # Heap + page cache sized for a 4 GiB droplet; raise for larger graphs
      NEO4J_server_memory_heap_initial__size: "1G"
      NEO4J_server_memory_heap_max__size: "1G"
      NEO4J_server_memory_pagecache_size: "512M"
    ports:
      # Bolt only — never expose the HTTP UI (7474) on a public droplet
      - "127.0.0.1:7687:7687"
    volumes:
      - neo4j_data:/data
      - neo4j_logs:/logs
    healthcheck:
      test: ["CMD-SHELL", "cypher-shell -u neo4j -p $$NEO4J_PASSWORD 'RETURN 1' || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 12
    networks:
      - group7_net

  backend:
    # ... existing config ...
    environment:
      # ... existing env ...
      SPRING_NEO4J_URI: bolt://neo4j:7687
      SPRING_NEO4J_AUTHENTICATION_USERNAME: neo4j
      SPRING_NEO4J_AUTHENTICATION_PASSWORD: ${NEO4J_PASSWORD}
      APP_RECOMMENDATIONS_FOLLOW_SYNC_ENABLED: "true"
    depends_on:
      db:
        condition: service_healthy
      neo4j:
        condition: service_healthy

volumes:
  # ... existing volumes ...
  neo4j_data:
  neo4j_logs:
```

Set `NEO4J_PASSWORD` in `.env` (`openssl rand -base64 24`). Bring it up:

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d --build neo4j
docker compose -f docker-compose.prod.yml --env-file .env up -d backend
docker compose logs -f neo4j backend
```

The compose health check waits up to 3 minutes for Neo4j to finish boot + GDS class-loading before backend starts.

### Operational tasks

**Inspect the graph from the host:**

```bash
docker compose exec neo4j cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
  "MATCH (u:User) RETURN count(u) AS users, (MATCH ()-[r:FOLLOWS]->() RETURN count(r))[0] AS edges;"
```

**Check that GDS loaded:**

```bash
docker compose exec neo4j cypher-shell -u neo4j -p "$NEO4J_PASSWORD" \
  "CALL gds.list() YIELD name RETURN count(name) AS procedures;"
# Expect a number >= 200; if 0, the plugin failed to load.
```

**Force a full resync** (when the failed_graph_syncs table grew or you suspect drift):

```bash
# Connect to the running JVM and trigger via JMX, OR temporarily reduce the
# cron to fire in a minute, then restore. Either way:
docker compose logs backend | grep "Follow-graph resync"
# Look for "scheduled=true … reconciledEdges=N failedSyncRetries=M"
```

**Rotate the Neo4j password:**

```bash
# 1. Set new password in the running instance
docker compose exec neo4j cypher-shell -u neo4j -p "$OLD" \
  "ALTER USER neo4j SET PASSWORD '$NEW'"
# 2. Update .env
sed -i "s|^NEO4J_PASSWORD=.*|NEO4J_PASSWORD=$NEW|" .env
# 3. Restart backend (Neo4j container does not need to restart — auth state lives in the data volume)
docker compose -f docker-compose.prod.yml --env-file .env up -d backend
```

**Backup the data volume:**

```bash
docker compose exec neo4j neo4j-admin database dump neo4j --to-stdout > \
  "neo4j-$(date -u +%Y%m%dT%H%M%SZ).dump"
```

Restore with `neo4j-admin database load`. Note: the graph is fully reconstructible from PostgreSQL — if you ever lose the Neo4j volume, the resync job will rebuild it from `follows` on next startup. The dump is mostly a speed optimisation.

### Disabling Neo4j cleanly

If you decide to back out of the advanced pipeline:

1. Set `APP_RECOMMENDATIONS_FOLLOW_SYNC_ENABLED=false` in `.env`.
2. `docker compose -f docker-compose.prod.yml --env-file .env up -d backend`.
3. The follow-recommendation endpoint immediately falls back to interest-only scoring.
4. Stop and remove the `neo4j` service: `docker compose stop neo4j && docker compose rm -f neo4j`.
5. Optional: keep the volume (`neo4j_data`) until you're sure you won't re-enable, then `docker volume rm bounswe2026group7_neo4j_data`.

The `failed_graph_syncs` PG table is harmless when sync is off — rows accumulate only when the listener fires, which is itself gated by the flag. You can leave the table as-is or `TRUNCATE` it.

---

## Health checks and observability

### Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /actuator/health` | Liveness + readiness aggregate. `{"status":"UP"}` when DB connection + disk space healthy |
| `GET /actuator/health/liveness` | Liveness only (process up) |
| `GET /actuator/health/readiness` | Readiness only (DB connected, schema migrated) |
| `GET /v3/api-docs` | OpenAPI spec |
| `GET /swagger-ui.html` | Live API explorer (disabled in `prod` profile) |

The Docker healthcheck in `docker-compose.prod.yml` polls `/actuator/health` every 10s with 20 retries — that gives roughly 3 minutes of startup grace for Flyway to apply migrations on first boot against a cold managed Postgres.

### Logs

Container logs:

```bash
docker compose logs -f backend                           # follow
docker compose logs --since 10m backend                  # last 10 min
docker compose logs backend | grep -E 'ERROR|Caused by'  # errors only
```

Logback configuration is at `backend/src/main/resources/logback-spring.xml`. The `prod` profile sets root level to `WARN` and `com.group7.backend` to `INFO`. Every log line carries a per-request UUID under `[req:...]` so you can grep a whole request's lifecycle.

### Metrics

Spring Boot Actuator's metrics endpoint is enabled at `/actuator/metrics` (no Prometheus scraper wired by default; add the `micrometer-registry-prometheus` dependency and `/actuator/prometheus` if you want one).

---

## Updates and rollouts

### Automatic (GitHub Actions)

Push to `main` → `.github/workflows/deploy.yml` triggers → SSH into droplet → `git pull && docker compose up --build -d`. Downtime is one container restart (~10s) plus health-check wait (~30s for warm DB).

### Manual

```bash
ssh root@<droplet-ip>
cd /root/bounswe2026group7
git fetch origin
git checkout main
git pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --build backend
docker compose -f docker-compose.prod.yml logs -f backend
```

### Rolling back

The simplest rollback is `git checkout <previous-commit>` and rebuild:

```bash
git checkout <commit-sha>
docker compose -f docker-compose.prod.yml --env-file .env up -d --build backend
```

If the rollback crosses a Flyway migration, the older code may not understand the newer schema. Flyway never auto-downgrades — you have to manually undo the migration via a forward "revert" migration. Prefer fix-forward: write a new commit that adapts to the bad state, deploy that, then plan a proper schema change.

### Zero-downtime upgrades

The compose-based deploy does a brief stop/start. For true zero-downtime, run two backend replicas behind nginx and roll them one at a time, OR use a managed orchestrator (DO App Platform, Kubernetes). Both are bigger changes — out of scope for the current single-droplet setup.

---

## Operational runbook

### Bootstrap the first admin

```bash
# In .env, one-shot
APP_ADMIN_BOOTSTRAP_ENABLED=true
APP_ADMIN_BOOTSTRAP_EMAIL=admin@example.com
APP_ADMIN_BOOTSTRAP_PASSWORD='<temporary password>'
APP_ADMIN_BOOTSTRAP_FIRST_NAME=Site
APP_ADMIN_BOOTSTRAP_LAST_NAME=Admin

# Apply
docker compose -f docker-compose.prod.yml --env-file .env up -d backend
docker compose logs backend | grep -i 'admin bootstrap'

# After the admin exists, lock it down
sed -i 's/APP_ADMIN_BOOTSTRAP_ENABLED=true/APP_ADMIN_BOOTSTRAP_ENABLED=false/' .env
# Remove the password line entirely:
sed -i '/APP_ADMIN_BOOTSTRAP_PASSWORD/d' .env
docker compose -f docker-compose.prod.yml --env-file .env up -d backend
```

### Rotate JWT secret

Generate a new secret and redeploy:

```bash
NEW=$(openssl rand -base64 32)
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$NEW|" .env
docker compose -f docker-compose.prod.yml --env-file .env up -d backend
```

All existing JWTs become invalid — every user gets logged out and must sign in again. Plan the rotation during low-traffic hours.

### Rotate DB password

1. Change the password in the DO control panel.
2. Update `SPRING_DATASOURCE_PASSWORD` in `.env`.
3. `docker compose -f docker-compose.prod.yml --env-file .env up -d backend`.

### Apply a hotfix migration

1. Open a hotfix branch from `main`.
2. Add `V<max+1>__<description>.sql`.
3. Run `mvn verify` locally against a throwaway Postgres to catch errors early.
4. Open PR → CI runs → merge to `main` → deploy workflow ships.

### Inspect a stuck request

```bash
# Grep the per-request UUID in logs
docker compose logs backend | grep -A 20 '[req:e8b2c1a0-...]'
```

The `MDC` filter sets a UUID at the top of every request and clears it on exit; long-running async work tagged with `@Async` propagates it via `MdcCopyingTaskDecorator`.

### Free disk: clear orphan attachments early

The nightly sweep at 03:15 UTC reclaims uploads older than `UPLOAD_ORPHAN_RETENTION_HOURS`. To run it on demand without waiting:

```bash
# Connect to the running JVM via JMX, OR temporarily lower retention:
echo "UPLOAD_ORPHAN_RETENTION_HOURS=1" >> .env
docker compose -f docker-compose.prod.yml --env-file .env up -d backend
# (wait 1h, or trigger the scheduled method via a /actuator/scheduledtasks call if exposed)
# Restore default after:
sed -i '/UPLOAD_ORPHAN_RETENTION_HOURS/d' .env
docker compose -f docker-compose.prod.yml --env-file .env up -d backend
```

---

## Troubleshooting

### Backend won't boot — `Detected resolved migration not applied to database`

Flyway saw a migration file with a version number lower than the highest already-applied version, and `out-of-order=false` is the default. Two paths:

- **Renumber**: change `V42__foo.sql` to the next free number above `max(applied)`. Cleanest.
- **Allow out-of-order**: set `spring.flyway.out-of-order=true`. Accepts past-version inserts forever after — a policy decision, not a hotfix.

### Backend won't boot — `column "X" already exists`

A previous migration partially applied (DDL committed) but the `flyway_schema_history` row never landed — usually because two migration files share the same operation across versions, or stale `target/classes/` carried an old migration file alongside its renamed successor.

```bash
# Confirm classpath has only one migration touching the column
find backend/target/classes/db/migration/ -name "*.sql" | xargs grep -l 'ADD COLUMN.*X'

# Wipe and rebuild
cd backend && mvn clean
```

### Backend won't boot — `Ambiguous @ExceptionHandler method mapped for ...`

Two handlers in `GlobalExceptionHandler` mapped to the same exception class. Merge them into one; richer response shape wins.

### Email never arrives in production

1. Check `RESEND_API_KEY` is the **live** key (not `re_test_*`).
2. Check `APP_EMAIL_ENABLED=true` (compose-prod pins this, but `.env` overrides are read).
3. Resend dashboard → "Logs" tab — failed deliveries show the SMTP-level error.
4. `APP_MAIL_FROM` must use a domain you've verified in Resend.

### Rate limiter returns 429 for legitimate users

`APP_RATELIMIT_TRUSTED_PROXIES` is wrong. The limiter is keying on the wrong header value (e.g., the proxy's IP, so every request from any user shares a bucket). See section 6 of [`PRODUCTION_CHECKLIST.md`](../PRODUCTION_CHECKLIST.md).

### WebSocket disconnects with 1006 / 502

The reverse proxy is closing the upgrade. Make sure your nginx `location /ws/` block sets `proxy_http_version 1.1`, `Upgrade $http_upgrade`, `Connection "upgrade"`, and a `proxy_read_timeout` long enough for idle sockets (≥ 60s; 86400s in the sample above for chat that idles).

### Container OOMs or runs hot

The default JVM picks up container memory limits since Java 10. To pin explicitly:

```yaml
# docker-compose.prod.yml under backend:
environment:
  JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
mem_limit: 1g
```

A 1 GiB droplet is the practical floor; 2 GiB is comfortable.

### Neo4j: `Could not perform discovery` / `Connection refused` after enabling sync

Bolt is not reachable, or the password is wrong. The driver is lazy so the backend boots fine — the error surfaces only when the sync listener or PageRank query runs.

1. `docker compose exec neo4j cypher-shell -u neo4j -p "$NEO4J_PASSWORD" "RETURN 1"` from the host — confirms the password and that Bolt port is up inside the container.
2. From the backend container: `docker compose exec backend curl -fsS http://neo4j:7474 || echo "Neo4j HTTP unreachable — DNS or network"`. (The backend uses Bolt at 7687, but HTTP confirms network reachability.)
3. For Aura: the `neo4j+s://` URI uses TLS; `bolt://` will fail. Use the URI Aura gives you verbatim.

### Neo4j: GDS procedures missing — `There is no procedure with the name gds.pageRank.stream`

The plugin did not load. With the baked image (`backend/docker/neo4j-gds`) this should not happen, but if it does:

1. `docker compose logs neo4j | grep -iE 'GDS|plugin|loaded'` — look for `Loaded GDS X.Y.Z` at boot.
2. `docker compose exec neo4j ls -lah /var/lib/neo4j/plugins/` — confirm `neo4j-graph-data-science.jar` is present and non-zero bytes.
3. If the JAR is missing, the image was built before the `gds-fetcher` builder stage finished. Rebuild explicitly: `docker compose -f docker-compose.prod.yml --env-file .env build --no-cache neo4j`.
4. The `NEO4J_dbms_security_procedures_allowlist=gds.*` env var is baked into the image — overriding it in compose without including `gds.*` blocks GDS at the security layer even when the JAR is present.

### Neo4j: `failed_graph_syncs` table growing fast

The listener is queuing edges it can't write. Either Neo4j is intermittently down or under sustained load.

```bash
docker compose exec db psql -U group7 -d group7db \
  -c "SELECT COUNT(*), MIN(attempted_at), MAX(attempted_at) FROM failed_graph_syncs;"
```

If MIN is more than 24 hours ago, the nightly resync did not drain the queue — Neo4j is failing repeatedly. Check `docker compose logs neo4j` for OOM, disk-full, or auth errors. After fixing, trigger a backend restart so the resync listener picks up the backlog on its next cron fire.

### "Cannot connect to managed Postgres" after first boot

Three usual suspects:

1. The droplet is not in the database's Trusted Sources list. DO control panel → Database → Settings → Trusted Sources.
2. `sslmode=require` is missing from the JDBC URL. Managed PG refuses non-SSL connections.
3. The connection pool exhausted. `application-prod.properties` sets `hikari.maximum-pool-size=20`; raise if `[req:...]` lines log "connection acquired" waits over 100 ms.

---

## Pre-production checklist

Walk through [`PRODUCTION_CHECKLIST.md`](../PRODUCTION_CHECKLIST.md) at the repository root. The short version:

- [ ] `JWT_SECRET` regenerated from `openssl rand -base64 32`, not the default placeholder.
- [ ] `SPRING_DATASOURCE_PASSWORD` is a fresh non-default strong password.
- [ ] `RESEND_API_KEY` is a live key; `APP_MAIL_FROM` uses a Resend-verified domain.
- [ ] `APP_BASE_URL` and `APP_FRONTEND_URL` are HTTPS production URLs.
- [ ] `APP_CORS_ALLOWED_ORIGINS` lists production frontend(s) only — no localhost.
- [ ] `APP_RATELIMIT_ENABLED=true`, `APP_RATELIMIT_TRUST_XFF` and `..._TRUSTED_PROXIES` set to the actual proxy topology.
- [ ] `APP_ADMIN_BOOTSTRAP_ENABLED=false` after the first admin exists; password line removed.
- [ ] `APP_TEST_ENDPOINTS_ENABLED=false` (compose-prod hard-pins this, but verify your `.env` does not try to override).
- [ ] `SPRING_PROFILES_ACTIVE=prod` is set (compose-prod sets it; bare-JVM deploys must too).
- [ ] DB volume snapshot policy in place + restore drill done at least once.
- [ ] If the advanced follow-recommendation pipeline is on: Neo4j (Aura or self-hosted) reachable, `SPRING_NEO4J_*` set with a fresh password, `APP_RECOMMENDATIONS_FOLLOW_SYNC_ENABLED=true`, and `gds.list()` returns ≥ 200 procedures. Otherwise leave the flag `false` and skip Neo4j entirely.
- [ ] `.env` is `chmod 600`, not committed, not in `docker compose` build context (it's `.gitignore`d).
- [ ] TLS terminates in front of the backend; port 8080 is not reachable from the public internet.
- [ ] Backend `mvn verify` green on the commit you are about to deploy.

When all thirteen are checked, you are deployable.
