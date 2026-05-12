# BounSWE 2026 - Group 7

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

## Running with Docker

Make sure you have [Docker](https://docs.docker.com/get-docker/) and Docker Compose installed.

### Environment variables

Create a `.env` file in the project root (see `.env.example`):

```bash
cp .env.example .env
```

You can keep defaults for local development.

### Start all services

```bash
docker compose up --build
```

This starts three containers:

| Service  | URL                          | Description              |
|----------|------------------------------|--------------------------|
| Frontend | http://localhost:5174        | React + Vite dev server  |
| Backend  | http://localhost:8080        | Spring Boot REST API     |
| Swagger  | http://localhost:8080/swagger-ui.html | API docs        |
| MailHog  | http://localhost:8025        | Local email inbox (dev)  |
| Database | localhost:5433               | PostgreSQL               |

> **Note:** Port 5174 is used for the Docker frontend. If you run the frontend locally (`npm run dev`), it runs on 5173.

### Stop all services

```bash
docker compose down
```

To also remove the database volume:

```bash
docker compose down -v
```

---

## Running locally (without Docker)

### Prerequisites

- Java 21
- Maven
- Node.js 18+
- PostgreSQL running on port 5433 with:
  - Database: `group7db`
  - Username: `group7`
  - Password: `group7pass`

### Backend

```bash
cd backend
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend will be available at http://localhost:5173 and proxies `/api` requests to the backend at port 8080.

---

## Seeding mock data

A fresh database has no users beyond the bootstrapped admin (see [Default credentials](#default-credentials)). To populate the system with realistic personas, mentorships, tasks, meetings, and feed posts, run the persona seeder against the running backend:

```bash
cd scripts
python3 seed_personas.py
```

The script is idempotent (re-runnable on an existing database) and creates:

- **50 personas** — a mix of mentors and mentees with varied locations, interests, and capacity. All seeded accounts share the password `Seed1234!`. Emails follow the pattern `<slugified-first>.<slugified-last>.<id>@seed.test` (e.g. `Dr. Ahmet Bulut` with persona id `2` becomes `dr.ahmet.bulut.2@seed.test`).
- **Mentorship edges** — a handful of accepted, pending, and rejected mentorship requests so the matching, request, and dashboard surfaces all have something to show.
- **Demo ecosystem** — mentorship messages, tasks with due dates, meetings (including a pending reschedule request), and feed posts with hashtags so the social feed, calendar, and notification surfaces aren't empty on launch.

The seeder also writes `scripts/personas.json` — a flat fixture used by the mobile/web QA acceptance flows.

### Prerequisites for seeding

1. Backend reachable at `http://localhost:8080` (either via `docker compose up` or `mvn spring-boot:run`).
2. MailHog reachable at `http://localhost:8025` (the seeder uses MailHog's REST API to fetch verification tokens automatically — included in `docker compose`).
3. Python 3.10+ (stdlib only — no `pip install` step required).

If verification tokens are missing (e.g., emails went to a real SMTP server in production), the script falls back to logging the failure for that persona and continues with the rest.

### Resetting before re-seeding

To start from a clean database:

```bash
docker compose down -v        # drops the postgres volume
docker compose up -d          # backend boots, bootstraps the admin
cd scripts && python3 seed_personas.py
```

---

## Default credentials

The bootstrap admin is created on first backend startup against an empty `users` table — controlled by the `APP_ADMIN_BOOTSTRAP_*` variables in `.env.example`. Every persona created by `seed_personas.py` shares the same seed password.

| Role | Email | Password | Notes |
|------|-------|----------|-------|
| Admin | `admin@group7.com` | `Admin1234!` | Created on first backend startup from `.env.example`. Set `APP_ADMIN_BOOTSTRAP_ENABLED=false` after first login on a production install and change the password immediately. |
| Mentor (Data Science) | `dr.ahmet.bulut.2@seed.test` | `Seed1234!` | Verified mentor; one of his active mentees is Elif (below). |
| Mentee (Data Science) | `elif.yilmaz.1@seed.test` | `Seed1234!` | Verified mentee actively paired with Dr. Ahmet Bulut. |
| Mentee (banned fixture) | `eren.kilic.5@seed.test` | `Seed1234!` | Marked banned in `personas.json`; the ban flag itself is set via the admin ban endpoint (the seeder does not apply it automatically). |
| Mentee (unverified) | `ayse.kaya.6@seed.test` | n/a | Fixture for the unverified / verification-prompt surface. The account exists but cannot log in until verification is completed. |

The full 50-persona list is in `scripts/personas.json` after the seeder runs.
