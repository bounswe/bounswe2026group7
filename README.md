# BounSWE 2026 - Group 7

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
