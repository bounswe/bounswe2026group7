# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Running E2E (Playwright)

End-to-end specs live in `e2e/` and run against a real backend + Vite preview.
The auth flows pull verification / password-reset tokens from the profile-gated
`/api/test/**` endpoints (#315), so the backend must be started with the test
flags on:

```sh
APP_TEST_ENDPOINTS_ENABLED=true APP_EMAIL_ENABLED=false \
  ./mvnw -pl backend spring-boot:run
```

In production these flags default to `false`; the controller bean is not
registered and `SecurityConfig` 404s the path.

First-time setup (downloads browser binaries):

```sh
npm run test:e2e:install
```

Run the full suite (chromium + firefox + webkit):

```sh
npm run test:e2e
```

Smoke only (chromium, <30s, for fast PR feedback):

```sh
npx playwright test --grep @smoke
```

Single browser:

```sh
npx playwright test --project=chromium
```

Interactive UI mode for local debugging:

```sh
npm run test:e2e:ui
```

`E2E_BASE_URL` (frontend) and `E2E_BACKEND_URL` (backend) override the defaults
of `http://localhost:8000` and `http://localhost:8080`. CI sets `CI=true`,
which enables 2 retries and disables Playwright's auto-spawned vite dev server
so the workflow can manage backend/frontend lifecycle itself.
