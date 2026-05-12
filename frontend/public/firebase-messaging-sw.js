// Firebase Messaging service worker (#447). Companion to mobile's
// expo-notifications setup in mobile-app/lib/pushNotifications.ts —
// both register against the same backend FCM project (#136).
//
// The SW lives at a stable root URL (Firebase's SDK looks for exactly
// /firebase-messaging-sw.js by convention) and is NOT bundled by Vite —
// it's served from public/ verbatim. That means we can't use
// import.meta.env here.
//
// To avoid baking the Firebase config into a static file at build time
// (which would leak through the public asset and complicate per-env
// deploys), we receive the config via postMessage from the main thread
// right after the SDK registers the worker. Until that message arrives,
// the worker is inert — foreground delivery still works, background
// pushes simply aren't decoded into showNotification calls. The same
// session triggers the message on every SW boot, so background
// delivery starts as soon as the page first loads after a reboot.

importScripts('https://www.gstatic.com/firebasejs/10.13.0/firebase-app-compat.js')
importScripts('https://www.gstatic.com/firebasejs/10.13.0/firebase-messaging-compat.js')

let messaging = null

function initWithConfig(config) {
  if (messaging) return
  if (!config || !config.projectId) return
  try {
    firebase.initializeApp(config)
    messaging = firebase.messaging()
    messaging.onBackgroundMessage((payload) => {
      const title = (payload.notification && payload.notification.title) || 'New notification'
      const body = (payload.notification && payload.notification.body) || ''
      const click = (payload.data && payload.data.url) || '/notifications'
      self.registration.showNotification(title, {
        body,
        icon: '/favicon.svg',
        data: { url: click },
      })
    })
  } catch (e) {
    // Bad config or already initialised — stay inert.
  }
}

self.addEventListener('message', (event) => {
  const data = event.data || {}
  if (data.type === 'INIT_FIREBASE_CONFIG' && data.config) {
    initWithConfig(data.config)
  }
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = (event.notification.data && event.notification.data.url) || '/notifications'
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientsArr) => {
      const focused = clientsArr.find(c => c.url.indexOf(self.location.origin) === 0)
      if (focused) {
        focused.focus()
        if (typeof focused.navigate === 'function') {
          focused.navigate(url).catch(() => {})
        }
        return
      }
      return self.clients.openWindow(url)
    }),
  )
})
