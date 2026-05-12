# Backend — Spring Boot 3 (Java 21)

REST API for the mentorship platform. Auth, profiles, mentorships, social feed, messaging, reporting, admin moderation, real-time STOMP push, and the LLM-backed mentor recommender all live here.

For the full project quick start (Docker, ports, mock data, default credentials), see the **root [`README.md`](../README.md)**. This document covers backend-only details: local Maven workflow, env vars, profiles, testing, and operational notes.

---

## 1. Prerequisites

- **JDK 21** (Eclipse Temurin recommended)
- **Maven 3.9+** (or use the bundled `./mvnw` wrapper from the repo root)
- **PostgreSQL 16** on port `5433` (`group7db` / `group7` / `group7pass`) — `docker compose up db` from the repo root spins this up.
- **Neo4j 5** with the Graph Data Science plugin on port `7687` — `docker compose up neo4j` brings this up. Required for the follow-recommendation graph queries.
- (Dev only) **MailHog** on `1025` / `8025` — captures outbound mail. `docker compose up mailhog`.

The simplest path: run `docker compose up db neo4j mailhog` from the repo root, then run the backend locally against those containers (next section).

---

## 2. Run locally

```bash
cd backend
mvn spring-boot:run
```

Defaults: connects to `localhost:5433/group7db`, binds the HTTP API on port `8080`, talks Bolt to `localhost:7687`.

Override anything in [`application.properties`](src/main/resources/application.properties) via env vars — Spring Boot's relaxed binding handles `JWT_SECRET`, `SPRING_DATASOURCE_URL`, `OPENAI_API_KEY`, etc.

### Run with the test profile (Playwright fixtures)

```bash
APP_TEST_ENDPOINTS_ENABLED=true APP_EMAIL_ENABLED=false APP_SPAM_ENABLED=false \
  mvn spring-boot:run
```

This unlocks the [`/api/test/**`](src/main/java/com/group7/backend/controller/TestSupportController.java) surface (seed user, reset DB, fetch verification token, advance scheduler clock). The controller bean is gated by `@ConditionalOnProperty(name = "app.test-endpoints.enabled")` and SecurityConfig 404s the same paths as defense-in-depth — it never wires up under default production settings.

### Run with the production profile

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

Loads [`application-prod.properties`](src/main/resources/application-prod.properties) on top of the base. The Docker production build (`docker-compose.prod.yml`) sets this for you.

---

## 3. Build

```bash
mvn package -DskipTests           # ./target/backend-*.jar
mvn package                       # also runs the test suite (slower)
docker build -t group7-backend .  # Production image (matches docker-compose.prod.yml)
```

The Dockerfile is a two-stage build: Maven layer caches `dependency:go-offline`, then copies sources and builds a fat jar; runtime layer is a `temurin:21-jre` with `curl` for the healthcheck only.

---

## 4. Environment variables

Every backend env var is documented in the repo-root [`.env.example`](../.env.example) and [`.env.production.example`](../.env.production.example). Backend-specific notes:

| Variable                          | Purpose                                                                                            |
| --------------------------------- | -------------------------------------------------------------------------------------------------- |
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | Postgres connection. In dev, defaulted to the compose DB.                                  |
| `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` | Bolt connection for the follow-recommendation graph queries.                       |
| `JWT_SECRET`                      | Base64-encoded HMAC-SHA256 secret. **Generate fresh for prod**: `openssl rand -base64 64`.        |
| `JWT_EXPIRATION`                  | Token TTL in ms (default 24h).                                                                     |
| `APP_BASE_URL`                    | Used in verification-email links and AS 2.0 canonical IRIs (`FeedIriBuilder`).                     |
| `APP_FRONTEND_URL`                | Used in password-reset email links.                                                                |
| `APP_CORS_ALLOWED_ORIGINS`        | Comma-separated CORS whitelist.                                                                    |
| `APP_ADMIN_BOOTSTRAP_ENABLED`     | When `true`, [`AdminBootstrapper`](src/main/java/com/group7/backend/config/AdminBootstrapper.java) creates the configured Admin on startup (idempotent). |
| `APP_ADMIN_BOOTSTRAP_EMAIL/PASSWORD/FIRST_NAME/LAST_NAME` | The Admin to bootstrap. Password validation enforces ≥12 chars + complexity.  |
| `APP_TEST_ENDPOINTS_ENABLED`      | `/api/test/**` seed/reset surface. **Pinned to `false` in `docker-compose.prod.yml`.**             |
| `APP_EMAIL_ENABLED`               | When `false`, swaps `EmailService` for a `NoOpEmailService` — useful for E2E and local seed runs.  |
| `APP_SPAM_ENABLED`                | Toggles the spam-bot / honeypot defences on `/api/auth/register`.                                  |
| `OPENAI_API_KEY`                  | Spring AI — embeddings + chat. Optional; the ranker degrades to a rule-based legacy path if unset. |
| `MENTOR_ADVANCED_RANKER`          | Flip the mentor recommender pipeline (#436).                                                       |
| `MENTOR_EXPLANATION_ENABLED`      | Toggles LLM prose match explanations (#585).                                                       |
| `FEED_ADVANCED_RANKER`            | Swaps the For-You feed ranker for the advanced LLM-augmented variant (#438).                       |
| `RESEND_API_KEY`                  | Production mail sender (Resend.dev). Dev defaults to MailHog SMTP.                                 |
| `SPRING_MAIL_*`                   | Standard Spring Mail JavaMailSender properties. Dev defaults to MailHog (`mailhog:1025`).          |

---

## 5. Database

- Postgres 16 is the system of record; Hibernate uses `validate` mode against Flyway migrations.
- All schema is managed by **Flyway**. Migrations live under [`src/main/resources/db/migration/`](src/main/resources/db/migration/) and run automatically at boot.
- Latest migration version on `dev`: `V55__add_mentor_profile_visibility.sql` (53 migrations total).
- Neo4j is used **only** for the follow-recommendation graph queries. The graph is rebuilt from Postgres on demand; losing the Neo4j volume is non-fatal.

---

## 6. API documentation

- **OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Swagger UI**: http://localhost:8080/swagger-ui.html

Every controller is fully annotated with `@Operation` / `@ApiResponses` / `@Parameter` — the live spec is the canonical contract.

Feed surfaces also support **W3C Activity Streams 2.0 / Schema.org JSON-LD** content negotiation: send `Accept: application/ld+json` or `application/activity+json` and `GET /api/feed/posts/{id}` plus the paginated feed endpoints return AS 2.0 documents (with `OrderedCollectionPage` envelopes, `attributedTo`, `interactionStatistic`, etc.). Plain `application/json` requests are unaffected.

---

## 7. Testing

```bash
mvn test                       # full suite (~2300 tests, ~3–4 minutes)
mvn test -Dtest=UserServiceTest # single class
mvn -Dtest='*Integration*' test # integration suite only
mvn verify                      # tests + JaCoCo coverage gate
```

- **Unit tests** — Mockito, JUnit 5; no Spring context.
- **Slice tests** — `@WebMvcTest`, `@DataJpaTest` against H2 in-memory.
- **Integration tests** — `@SpringBootTest` against Testcontainers Postgres (real DB engine, real Flyway).
- **STOMP integration** — `FeedRealtimeIntegrationTest` drives the WebSocket stack end-to-end for the live engagement push (#566).

`mvn verify` enforces the JaCoCo line-coverage gate per package — CI fails the PR if coverage drops.

---

## 8. Operational notes

- **Healthcheck**: `GET /actuator/health` (used by the Docker compose healthcheck).
- **Logging**: structured Logback config in [`logback-spring.xml`](src/main/resources/logback-spring.xml). `org.hibernate.orm.jdbc.bind` is pinned to `INFO` even in dev so PII in JPA parameters doesn't leak (#135).
- **Test endpoints**: every route under `/api/test/**` is gated by `@ConditionalOnProperty(name = "app.test-endpoints.enabled", havingValue = "true")` AND a SecurityConfig 404 fallback. In prod the bean never enters the context.
- **Rate limiting**: Bucket4j per-route rules in [`application.properties`](src/main/resources/application.properties) under `app.ratelimit.rules.*` — auth, feed-create, report-create, comment, etc.
- **Async fan-out**: the feed (#356/#566) and report (#135) systems publish events via `@TransactionalEventListener(AFTER_COMMIT) + @Async`. The thread pool is configured in [`AsyncConfig`](src/main/java/com/group7/backend/config/AsyncConfig.java).

---

## 9. Project layout

```
backend/
├── Dockerfile                Two-stage Maven → JRE build
├── pom.xml
├── docker/neo4j-gds/         Custom Neo4j image with the GDS plugin baked in
└── src/
    ├── main/
    │   ├── java/com/group7/backend/
    │   │   ├── config/            Spring config + bootstrappers (AdminBootstrapper, JsonLd*, Security, Async)
    │   │   ├── controller/        REST + STOMP controllers
    │   │   ├── dto/               Request + response DTOs
    │   │   ├── entity/            JPA entities
    │   │   ├── event/             ApplicationEvents + AFTER_COMMIT listeners
    │   │   ├── exception/         Domain exceptions + GlobalExceptionHandler
    │   │   ├── repository/        Spring Data JPA + Neo4j repositories
    │   │   ├── scheduler/         @Scheduled tasks (mentorship auto-complete, reminders, …)
    │   │   ├── service/           Business logic
    │   │   └── validation/        Custom @ValidPassword, @ValidEscoUri, etc.
    │   └── resources/
    │       ├── application.properties        Base config
    │       ├── application-prod.properties   Prod overrides
    │       ├── db/migration/                 Flyway migrations (V1 … V55)
    │       ├── taxonomy/                     ESCO / ISCED-F / Wikidata fixtures
    │       └── logback-spring.xml
    └── test/                                 Mirror structure, ~2300 tests
```
