/**
 * Service-worker stub for AT-06 (#317).
 *
 * The frontend doesn't currently register an FCM service worker
 * (`firebase-messaging-sw.js`) and doesn't call POST /api/users/me/devices,
 * so true push delivery via the browser SW is impossible to assert today.
 * What this stub does:
 *
 *   1. Intercept any request for `/firebase-messaging-sw.js` (and a couple
 *      of common variants) and serve a no-op SW so the navigator's
 *      `serviceWorker.register(...)` call (if/when frontend lands one) does
 *      not 404 in CI.
 *   2. Expose a `__pushReceived` array on the `window` so a future test that
 *      does drive a push can capture invocations without touching real FCM.
 *
 * Right now the AT-06 spec asserts the in-app notification path
 * (NotificationsPage row appears for a MEETING_REMINDER) — which IS the
 * production data path — and skips push-delivery assertion with a
 * documented `test.fixme`. This stub is the scaffolding for unfixming once
 * the frontend SW + device token registration land.
 */
export async function installServiceWorkerStub(context) {
  // Serve a no-op SW for any route that looks like a Firebase messaging
  // worker. Done via `route` so it works without changes to dev server or
  // build output.
  await context.route(
    /\/firebase-messaging-sw(\.[^/]+)?\.js$/,
    route => route.fulfill({
      status: 200,
      contentType: 'application/javascript',
      body: `// Playwright SW stub for AT-06\n`
        + `self.addEventListener('install', () => self.skipWaiting());\n`
        + `self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));\n`
        + `self.addEventListener('push', e => {\n`
        + `  const data = (() => { try { return e.data?.json(); } catch { return null; } })();\n`
        + `  e.waitUntil(self.clients.matchAll({ includeUncontrolled: true })\n`
        + `    .then(cs => cs.forEach(c => c.postMessage({ __pushReceived: true, data }))));\n`
        + `});\n`,
    }),
  );

  // Mirror anything posted from the SW back into a window-side array so
  // tests can read it once the frontend wires up real push handling.
  await context.addInitScript(() => {
    window.__pushReceived = [];
    if (navigator.serviceWorker) {
      navigator.serviceWorker.addEventListener('message', (event) => {
        if (event.data?.__pushReceived) {
          window.__pushReceived.push(event.data);
        }
      });
    }
  });
}
