# Frontend — React 19 + Vite

The web client for the mentorship platform. React 19, React Router 6, STOMP-over-WebSocket for live updates, Vitest for unit tests, Playwright for end-to-end specs.

For the full project quick start (Docker, ports, mock data, default credentials), see the **root [`README.md`](../README.md)**. This document covers frontend-only details: local dev workflow, env vars, testing, and production builds.

---

## 1. Prerequisites

- **Node.js 20+** (CI uses Node 22; the Docker image is `node:22-alpine`)
- **npm 10+** (ships with Node)
- A running backend on `http://localhost:8080` for any non-trivial flow. The simplest way to get one: `docker compose up backend db neo4j mailhog` from the repo root.

---

## 2. Install + run (development)

```bash
cd frontend
npm install
npm run dev
```

Vite dev server: http://localhost:8000. All `/api/**` requests are proxied to `VITE_BACKEND_URL` (default `http://localhost:8080`); STOMP-over-WebSocket on `/ws/chat` is proxied with `ws: true` so feed and conversation live-updates work locally.

To point at a non-default backend (e.g. a deployed staging API):

```bash
VITE_BACKEND_URL=https://staging.mymentornet.org npm run dev
```

---

## 3. Build (production)

```bash
npm run build      # Outputs static bundle to ./dist
npm run preview    # Serves the prod bundle on port 8000 (production sanity check)
```

The Docker production build (`docker-compose.prod.yml`) runs `npm run build` then serves the bundle via the same Vite preview server on port `8000`. `VITE_BACKEND_URL` is baked in at build time, so the prod build must be produced with the right value:

```bash
VITE_BACKEND_URL=https://your-domain.com npm run build
```

---

## 4. Environment variables

The frontend reads exactly two env vars; both are documented in the repo-root [`.env.example`](../.env.example) and [`.env.production.example`](../.env.production.example).

| Variable             | Purpose                                                                                  | Default                          |
| -------------------- | ---------------------------------------------------------------------------------------- | -------------------------------- |
| `VITE_BACKEND_URL`   | Origin of the backend REST + WebSocket API. Used at build time AND by the dev-server proxy. | `http://localhost:8080`        |
| `FRONTEND_PORT`      | Host port the container binds (compose only; Vite still listens on 8000 internally).     | `8000` (dev) / `80` (prod)       |

Vite's `import.meta.env.*` only reads `VITE_*`-prefixed vars; nothing else from `.env` reaches the bundle.

---

## 5. Testing

Two distinct test stacks, intentionally isolated (see `vite.config.js` — Vitest's `exclude` keeps `e2e/` out of the unit run so Playwright specs don't get loaded by the wrong runner).

### Unit + component tests (Vitest)

```bash
npm test                       # Headless, single-pass (CI mode)
npm test -- --watch            # Watch mode
npm test -- src/pages/__tests__/AdminConsolePage.test.jsx   # Single file
```

- Stack: Vitest + Testing Library + jsdom.
- Setup: `src/test/setup.js` (jest-dom matchers, fetch polyfill, etc.).
- Component tests live under `src/**/__tests__/*.test.jsx`; hook tests under `src/hooks/__tests__/*.test.jsx`.

### End-to-end tests (Playwright)

```bash
# One-time browser install (~250 MB)
npm run test:e2e:install

# Run the full E2E suite. Requires backend + frontend on the local stack.
npm run test:e2e

# Interactive runner with trace viewer
npm run test:e2e:ui
```

Specs live under [`e2e/`](e2e/). They drive the AT-01 through AT-16 acceptance scenarios.

**Critical**: the backend must be started with the test-fixture flags on, so the auth + scheduler test surfaces are reachable:

```bash
APP_TEST_ENDPOINTS_ENABLED=true APP_EMAIL_ENABLED=false APP_SPAM_ENABLED=false \
  ./mvnw -pl backend spring-boot:run
```

In production these flags default to `false`; the test controller bean is gated by `@ConditionalOnProperty` and the SecurityConfig 404s the test routes as defense in depth.

---

## 6. Lint

```bash
npm run lint
```

Flat-config ESLint 9 with `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`. Run before opening a PR.

---

## 7. Architecture notes

- **Auth**: JWT stored in `localStorage`, attached to every fetch via the shared helpers in [`src/services/api.js`](src/services/api.js). A shared response interceptor surfaces `403 BANNED_UNTIL` via a `CustomEvent('auth:banned')` so `AuthContext` and the global `BannedStateBanner` can react without coupling.
- **Routing**: [`src/App.jsx`](src/App.jsx). `ProtectedRoute` gates authenticated routes; `AdminRoute` additionally requires `role === 'ADMIN'`. Server-side `@PreAuthorize` is the actual security boundary — the client guards are UX, not authorization.
- **Real-time**: STOMP-over-WebSocket via `@stomp/stompjs`. Two subscriptions: `/topic/feed.{userId}` (new posts + share/repost + live engagement counts, #356/#566) and `/topic/conversation.{conversationId}` (DM live updates). Hooks: [`src/hooks/useFeedSubscription.js`](src/hooks/useFeedSubscription.js) and [`src/hooks/useConversationSubscription.js`](src/hooks/useConversationSubscription.js).
- **Selector contract**: every Playwright-targeted element carries a `data-testid` attribute; the canonical name list lives in [`e2e/testids.js`](e2e/testids.js).

---

## 8. Project layout

```
frontend/
├── Dockerfile            node:22-alpine dev server (host mode)
├── package.json
├── vite.config.js        Dev server + proxy + Vitest configuration
├── playwright.config.js  E2E runner config (browsers, retries, base URL)
├── public/               Static assets served as-is
├── src/
│   ├── App.jsx           Route table
│   ├── main.jsx          React root
│   ├── components/       Reusable UI (FeedPostCard, MentorshipMilestones, …)
│   ├── pages/            Top-level screens
│   ├── hooks/            Custom hooks (useFeedSubscription, useAuth, …)
│   ├── context/          React context providers (AuthContext, MentorshipContext)
│   ├── services/         REST + WebSocket clients (api.js, stompClient.js)
│   ├── utils/            Date formatting, toast helper, etc.
│   ├── styles/           Global CSS
│   └── test/             Vitest setup + shared fixtures
└── e2e/
    ├── fixtures/         Playwright fixtures (auth context, seed helpers)
    ├── pages/            Page Object Models
    ├── specs/            AT-01 … AT-16 acceptance test specs
    └── testids.js        Selector constants
```
