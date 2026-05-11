# Backend Tests

This directory holds the JUnit 5 test suite for the Spring Boot backend. It covers four layers, each with a different purpose, scope, and cost.

## 1. Test layers — when to pick each

| Layer | Use when | Annotations | Cost |
|---|---|---|---|
| Unit | Pure logic, no Spring | none | µs |
| Controller slice | Validating request shape, status codes, security wiring | `@WebMvcTest` + `@Import(SecurityConfig.class, JwtAuthenticationFilter.class)` + `@MockitoBean` for the service | seconds |
| Integration | One feature exercised through the full Spring context against PostgreSQL | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` | tens of seconds |
| **End-to-end** | One scenario that crosses multiple features | extends `AbstractE2ETest` | tens of seconds (one context for the whole class) |

Pick the lowest layer that catches the regression you care about. A controller slice is the right home for "does this endpoint return 400 on a missing field?"; an E2E is the right home for "does the request → accept → meeting → task → rate flow still work end-to-end?"

## 2. Running locally

```bash
docker compose up -d postgres                    # Postgres on port 5433
nc -z localhost 5433 && echo "OK"                # confirm it's up
mvn verify                                       # all tests
mvn test -Dtest=EndToEndWorkflowTest             # just the cross-feature suite
mvn test -Dtest=MentorshipControllerTest         # just one slice test
```

If a previous branch left an incompatible Flyway migration in your local DB (Flyway will surface this as a `Validate failed: Migrations have failed validation` error on context boot), wipe the schema:

```bash
docker exec bounswe2026group7-db-1 psql -U group7 -d group7db \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

## 3. The TestDataBuilder pattern (E2E)

Cross-feature scenarios compose user journeys from fluent builders rooted at `api`, an `E2EClient` instance provided by `AbstractE2ETest`. Each builder gathers state and calls a terminal method that drives MockMvc and returns a typed result.

```java
class FooScenarioTest extends AbstractE2ETest {
    @Test
    void mentorRequestsFlow() throws Exception {
        UserHandle mentor = api.users().asMentor()
                .email("foo_mentor@test.com").firstName("Mira")
                .registerVerifyAndLogin();
        setMentorCapacity(mentor, 1);

        UserHandle mentee = api.users().asMentee()
                .email("foo_mentee@test.com").firstName("Mehmet")
                .registerVerifyAndLogin();

        Long requestId = api.requests().from(mentee).to(mentor).message("Hi").create();
        Long mentorshipId = api.acceptRequest(mentor, requestId, 3);
        api.setSharedGoal(mentor, mentorshipId, "Build a portfolio");
        // ... continue with meetings, tasks, ratings, ...
    }
}
```

Available builders: `users()`, `requests()`, `meetings()`, `tasks()`, `availability()`, `ratings()`. One-shot actions (accept / reject / cancel / confirm / submit / review / set goal) live as direct methods on `E2EClient`.

## 4. Adding a new E2E scenario — checklist

1. **Extend `AbstractE2ETest`.** Do not redeclare `@SpringBootTest` — that creates a separate context and bloats CI.
2. **Use the builders.** Any plumbing you find missing belongs in a new builder method, not inline in the test.
3. **Keep each `@Test` under ~120 lines.** If you are over, split into two scenarios.
4. **Stay under ~15 endpoint calls per `@Test`.** Beyond that the test is exercising code that probably has its own integration test already.
5. **Avoid `Thread.sleep`.** If you genuinely need to wait on an async event, mock the event source or move time forward via `advanceClock(Duration)` — never wall-clock sleep.
6. **Do not annotate the test with `@Transactional`.** Production code commits across multiple endpoints; Spring rollback would not unwind cleanly.
7. **Pick deterministic emails** (e.g. `e2e_<scenario>_mentor@test.com`). Reuse across runs is fine — `cleanDb()` wipes everything between tests.

## 5. Anti-flake guide

Flakes in this suite come from a small handful of sources. The defaults below avoid all of them:

- **Wall clock** — pinned to a fixed Monday by `E2EClockConfig`. Any code path that takes a `Clock` will see the pinned time. If you need a Monday/Tuesday/etc., compute relative to `now()` or hard-code the literal `2026-05-11T...` (a Monday).
- **Email** — `EmailService` is mocked; the verification token is read from `verification_tokens` and POSTed to the real `/api/auth/verify-email` endpoint.
- **Push** — production wires `NoOpPushDeliveryService` automatically when no FCM credentials are present, so push delivery is a silent no-op in tests.
- **Schedulers** — disabled by default in `application-test.properties` (rate-limit, match-notification, meeting reminder, ban expiry, task reminder).
- **DB state** — `cleanDb()` wipes all tables touched by E2E flows in FK-dependency order between tests.
- **Shared static state** — none of the production beans hold mutable static state that survives a context. If you are tempted to add one, push it into a Spring bean instead.

## 6. Performance budget

- One Spring context per E2E class. Boot ≈ 15s on first test, ≈ 0s thereafter.
- Each scenario hits ≤ 15 endpoints, ≪ 100ms each.
- Target: every E2E class completes in < 60s wall time on CI; the suite as a whole stays well under the issue #254 budget of 2 minutes.
