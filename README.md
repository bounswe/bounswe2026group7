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
