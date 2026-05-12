# BounSWE 2026 — Group 7

Mentorship platform with a Spring Boot backend, a React + Vite web app, and a React Native (Expo) mobile app.

This document covers everything needed to deploy and run the **web** stack (backend + frontend + database) and to populate it with usable data. Mobile setup lives in [`mobile-app/README.md`](mobile-app/README.md).

---

## 1. Quick start (recommended)

Prerequisites: [Docker](https://docs.docker.com/get-docker/) with Docker Compose (any recent version with the `docker compose` plugin).

```bash
git clone https://github.com/bounswe/bounswe2026group7.git
cd bounswe2026group7
cp .env.example .env
docker compose up --build
```

The first build takes ~5–10 minutes (Postgres image, Neo4j+GDS image, Maven dependencies, npm install, frontend Vite preview build). Subsequent runs reuse cached layers and start in under a minute.

When the stack reports `backend` and `frontend` healthy, open:

| Service           | URL                                        | What it is                        |
| ----------------- | ------------------------------------------ | --------------------------------- |
| **Frontend (web)**| http://localhost:8000                      | React app (Vite preview build)    |
| **Backend (API)** | http://localhost:8080                      | Spring Boot REST API              |
| Swagger UI        | http://localhost:8080/swagger-ui.html      | Live OpenAPI 3 docs               |
| MailHog           | http://localhost:8025                      | Local email inbox (catches verification + reset mails) |
| PostgreSQL        | localhost:5433                             | Database (user `group7`, db `group7db`) |
| Neo4j             | http://localhost:7474 / bolt://localhost:7687 | Graph DB (follow recommendations) |

Stop the stack with `docker compose down`. Add `-v` to also wipe the database/Neo4j volumes for a clean slate.

---

## 2. Web Application

### 2.1 Docker compose files

- [`docker-compose.yml`](docker-compose.yml) — **development**. Bundles Postgres, Neo4j (with the GDS plugin baked into a custom image), MailHog, backend, and frontend. Sensible defaults for every environment variable; works out of the box after `cp .env.example .env`.
- [`docker-compose.prod.yml`](docker-compose.prod.yml) — **production**. Assumes an external managed Postgres (e.g. DigitalOcean), no Neo4j service, no MailHog. Requires `.env.production` with real secrets.

### 2.2 Environment variables (`.env.example`)

Every variable the backend or frontend reads is enumerated in [`.env.example`](.env.example) and [`.env.production.example`](.env.production.example). Both files are tracked in git; the corresponding `.env` / `.env.production` files are git-ignored.

Highlights:

| Variable                          | Purpose                                                          | Default (dev)                          |
| --------------------------------- | ---------------------------------------------------------------- | -------------------------------------- |
| `POSTGRES_DB` / `_USER` / `_PASSWORD` | Database name + credentials                                  | `group7db` / `group7` / `group7pass`   |
| `BACKEND_PORT` / `FRONTEND_PORT`  | Host ports for the API and web app                               | `8080` / `8000`                        |
| `JWT_SECRET`                      | Base64-encoded HMAC-SHA256 secret for signing JWTs               | Embedded dev key (NOT for production)  |
| `APP_BASE_URL` / `APP_FRONTEND_URL`| URLs embedded in verification + password-reset emails           | `http://localhost:8080` / `http://localhost:8000` |
| `APP_CORS_ALLOWED_ORIGINS`        | Comma-separated whitelist for CORS                               | `http://localhost:5173,...:5174,...:8000` |
| `APP_ADMIN_BOOTSTRAP_ENABLED`     | When `true`, the backend creates an Admin user on first boot (see 2.5) | `false`                          |
| `APP_TEST_ENDPOINTS_ENABLED`      | Enables `/api/test/**` seed/reset endpoints. **Never enable in production.** | `false`                  |
| `OPENAI_API_KEY` / `MENTOR_ADVANCED_RANKER` | Optional. Enables LLM-backed mentor ranking (#436)     | empty / `false`                        |
| `SPRING_MAIL_*`                   | Mail-relay configuration (defaults to MailHog)                   | MailHog defaults                       |

Production-only extras (see `.env.production.example`): `SPRING_DATASOURCE_URL`, `RESEND_API_KEY`, and a generated `JWT_SECRET` (`openssl rand -base64 64`).

### 2.3 Development setup

Same as the Quick Start above. After `docker compose up --build` is green:

1. Visit http://localhost:8000 — the registration / login surface.
2. Visit http://localhost:8025 — MailHog catches every outbound mail so verification + reset flows work locally with no real SMTP.

If you prefer to run pieces outside Docker, see the per-component guides — [`backend/README.md`](backend/README.md) and [`frontend/README.md`](frontend/README.md) — for Maven and Vite dev-server instructions.

### 2.4 Production setup

```bash
cp .env.production.example .env.production
# Fill in: SPRING_DATASOURCE_URL, JWT_SECRET, APP_BASE_URL,
#         APP_FRONTEND_URL, APP_CORS_ALLOWED_ORIGINS,
#         SPRING_MAIL_*, RESEND_API_KEY.
docker compose -f docker-compose.prod.yml --env-file .env.production up --build -d
```

Production differences vs. dev:

- No Postgres / Neo4j containers — uses a managed DB you supply via `SPRING_DATASOURCE_URL`.
- No MailHog — wire up a real SMTP relay (Gmail SMTP, Resend, SES, etc.).
- `APP_TEST_ENDPOINTS_ENABLED` is hard-pinned to `false` regardless of host env; the `/api/test/**` controller is also gated by [`@ConditionalOnProperty`](backend/src/main/java/com/group7/backend/controller/TestSupportController.java) so it never enters the Spring context.
- `SPRING_PROFILES_ACTIVE=prod` selects Spring's production profile.

Pre-deploy checklist: [`PRODUCTION_CHECKLIST.md`](PRODUCTION_CHECKLIST.md).

### 2.5 Data seeding (mock data)

The stack ships in an empty state. There are two complementary mechanisms to populate it.

#### A) Bootstrap a default Admin user (built-in, one shot on first boot)

Set three env vars in `.env` before bringing the stack up the first time:

```bash
APP_ADMIN_BOOTSTRAP_ENABLED=true
APP_ADMIN_BOOTSTRAP_EMAIL=admin@group7.local
APP_ADMIN_BOOTSTRAP_PASSWORD=Admin1234!ChangeMe
```

On startup the backend's [`AdminBootstrapper`](backend/src/main/java/com/group7/backend/config/AdminBootstrapper.java) checks for an existing admin with that email; if none exists, it creates one with `isEmailVerified=true`. Subsequent restarts are no-ops, so it's safe to leave the flag on. Once your admin exists you can flip the flag back to `false`.

#### B) Seed realistic mentor / mentee personas (recommended for grading / demo)

[`scripts/seed_personas.py`](scripts/seed_personas.py) registers 50 + 80 personas via the public `/api/auth/register` endpoint, pulls verification tokens from MailHog, then drives mentor↔mentee mentorships, tasks, meetings, and feed posts to give the UI something to render.

```bash
# Stack must already be running.
python3 scripts/seed_personas.py
```

Every persona's password is `Seed1234!`. Emails follow the pattern `{first}.{last}.{id}@seed.test`. The script writes the full persona index to `scripts/personas.json` after a successful run.

Estimated run time: ~3 minutes (rate-limited registrations). Re-running is idempotent — already-existing personas are logged and skipped.

### 2.6 Default credentials

After running the seed script and (optionally) the admin bootstrap, the following accounts are available out of the box:

| Role         | Email                          | Password         | Notes                            |
| ------------ | ------------------------------ | ---------------- | -------------------------------- |
| **Admin**    | `admin@group7.local`           | `Admin1234!ChangeMe` | Created by `APP_ADMIN_BOOTSTRAP_*` (2.5 A). Change before any non-local deployment. |
| **Mentor**   | `dr.ahmet.bulut.2@seed.test`   | `Seed1234!`      | Verified, has active mentorships and feed activity. |
| **Mentee**   | `elif.yilmaz.1@seed.test`      | `Seed1234!`      | Verified, paired with the mentor above. |

(Any persona from `scripts/personas.json` works — all share the `Seed1234!` password.)

---

## 3. Mobile Application

The mobile app is an Expo / React Native build. Setup, env files, network configuration, and `.apk` release artifact are documented in [`mobile-app/README.md`](mobile-app/README.md).

---

## 4. Where things live

```
bounswe2026group7/
├── backend/              Spring Boot 3 (Java 21) — see backend/README.md
├── frontend/             React + Vite + Playwright — see frontend/README.md
├── mobile-app/           Expo / React Native — see mobile-app/README.md
├── docker-compose.yml          Dev stack (db + neo4j + mail + backend + frontend)
├── docker-compose.prod.yml     Prod stack (backend + frontend only)
├── .env.example                Every dev env var, with defaults
├── .env.production.example     Production env var template
├── scripts/
│   ├── seed_personas.py        Realistic persona + mentorship seeder
│   └── seed_mentors*.py        Specialised mentor-focused variants
├── docs/                 Architecture notes and design records
└── PRODUCTION_CHECKLIST.md     Pre-deploy gate
```

---

## 5. Troubleshooting

- **`docker compose up` exits with `port is already allocated`.** Something else is bound to one of 8000 / 8080 / 5433 / 7474 / 7687 / 8025 / 1025. Either stop that process or override the port via `.env` (`BACKEND_PORT`, `FRONTEND_PORT`, `DB_PORT`, `NEO4J_*_PORT`, `MAILHOG_*_PORT`).
- **Frontend boots but every API call returns network error.** Check that `APP_CORS_ALLOWED_ORIGINS` in `.env` includes whichever origin you opened the app at, then restart the backend container.
- **Verification email never arrives.** In dev, mails go to MailHog at http://localhost:8025 — they don't reach a real inbox. In prod, ensure `SPRING_MAIL_*` and `APP_MAIL_FROM` point at a real relay.
- **`seed_personas.py` fails with HTTP 429.** Rate limiter hit. Wait a minute and re-run; the script is idempotent.
- **Backend health check stays red.** `docker compose logs backend | tail -50` — usually a missing migration or a Neo4j start-up race. The healthcheck retries 20 times so the most common races resolve on their own.

---

## 6. Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).
