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
required.

Stop the stack with `docker compose down`. Add `-v` to also drop the Postgres
and Neo4j volumes if you want a fully clean reset on the next boot.

### Seed local data

Right after a fresh `docker compose up`, the app is empty. The seed script
populates Postgres directly (bypassing email verification, Resend, and the
spam-bot defences) with **1 admin + 40 mentors + 60 mentees**, each with
realistic interests, bios, and goals.

```bash
pip install -r scripts/requirements-seed.txt
python3 scripts/seed_local.py
```

Every run is destructive-but-scoped: it deletes all prior `@seed.local` users
first, then re-inserts the roster. Real users you registered through the web
UI are never touched. Run it again whenever you want a clean demo dataset.

The script prints a credentials block to stdout (sample users per role) and
writes the full list — including each mentor's field and each mentee's major —
to `scripts/seed_local_credentials.txt`.

### Default credentials (after seeding)

All seeded passwords are deterministic. Sample accounts:

| Role   | Email                                            | Password      |
|--------|--------------------------------------------------|---------------|
| Admin  | `admin@seed.local`                               | `Admin1234!`  |
| Mentor | `mentor.<first>.<last>.<n>@seed.local` (40 total) | `Mentor1234!` |
| Mentee | `mentee.<first>.<last>.<n>@seed.local` (60 total) | `Mentee1234!` |

The first 5 mentors and 5 mentees are echoed when the script finishes; the
remaining accounts live in `scripts/seed_local_credentials.txt`.

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
