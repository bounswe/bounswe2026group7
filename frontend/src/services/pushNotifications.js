/**
 * Web push notifications (#447). Companion to the mobile implementation
 * in mobile-app/lib/pushNotifications.ts — both produce an FCM token
 * against the same Firebase project and register it via
 * POST /api/users/me/devices on the same backend (#136).
 *
 * This module is deliberately defensive:
 *   - All Firebase calls are gated on `isPushSupported()` so an env
 *     without the VITE_FIREBASE_* keys, an unsupported browser
 *     (no Notification API, no serviceWorker), or a private window
 *     simply produces a no-op rather than throwing on app boot.
 *   - The service worker file is at /firebase-messaging-sw.js and is
 *     served from public/ verbatim — Firebase's SDK insists on a
 *     stable root URL and looks there by convention.
 *
 * Returns a feature object the caller can use:
 *   { supported, configured, token | null }
 * `supported` = the browser supports Push API + Service Worker.
 * `configured` = VITE_FIREBASE_* env vars are present at build time.
 * Both true is required for actual delivery.
 */

import { initializeApp, getApps } from 'firebase/app'
import { getMessaging, getToken, onMessage, isSupported } from 'firebase/messaging'
import { registerDeviceToken, unregisterDeviceToken } from './api'

// Read Vite env. All-or-nothing — if any required field is missing we
// treat the build as unconfigured and never attempt to initialise.
const cfg = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}
const VAPID_KEY = import.meta.env.VITE_FIREBASE_VAPID_KEY

function envConfigured() {
  return Boolean(
    cfg.apiKey && cfg.projectId && cfg.messagingSenderId && cfg.appId && VAPID_KEY,
  )
}

export function isPushSupported() {
  if (typeof window === 'undefined') return false
  if (!('serviceWorker' in navigator)) return false
  if (!('Notification' in window)) return false
  if (!('PushManager' in window)) return false
  return true
}

export function isPushConfigured() {
  return envConfigured()
}

let cachedToken = null
let cachedMessaging = null

function getApp() {
  if (getApps().length > 0) return getApps()[0]
  return initializeApp(cfg)
}

async function getMessagingIfReady() {
  if (cachedMessaging) return cachedMessaging
  if (!envConfigured()) return null
  const supported = await isSupported().catch(() => false)
  if (!supported) return null
  cachedMessaging = getMessaging(getApp())
  return cachedMessaging
}

/**
 * Register a service worker, request notification permission if not
 * already granted, fetch the FCM token, and POST it to the backend.
 *
 * Returns the token on success, or null if anything in the chain is
 * unavailable. Never throws — all errors are swallowed and returned
 * as null so the caller can wire this into post-login without
 * making login itself fragile.
 *
 * Idempotent: re-running with the same token hits the backend's
 * `register` endpoint which refreshes lastSeenAt rather than
 * inserting a duplicate.
 */
export async function ensurePushRegistered() {
  if (!isPushSupported() || !envConfigured()) return null
  try {
    const reg = await navigator.serviceWorker.register('/firebase-messaging-sw.js')
    // Wait for the SW to actually be active before asking FCM for a token —
    // getToken requires an active worker, otherwise it throws.
    if (!reg.active) {
      await new Promise(resolve => {
        const installing = reg.installing || reg.waiting
        if (!installing) return resolve()
        installing.addEventListener('statechange', () => {
          if (installing.state === 'activated') resolve()
        })
      })
    }

    // Hand the Firebase config to the SW so its onBackgroundMessage
    // handler can decode pushes into showNotification calls. The SW is
    // static (not Vite-bundled) so it can't read import.meta.env on its
    // own. Resending on every register() call is intentional — survives
    // SW reboots without persisting secrets to IndexedDB.
    const target = reg.active || navigator.serviceWorker.controller
    if (target && typeof target.postMessage === 'function') {
      target.postMessage({
        type: 'INIT_FIREBASE_CONFIG',
        config: cfg,
      })
    }

    if (Notification.permission === 'default') {
      const perm = await Notification.requestPermission()
      if (perm !== 'granted') return null
    } else if (Notification.permission !== 'granted') {
      return null
    }

    const messaging = await getMessagingIfReady()
    if (!messaging) return null

    const token = await getToken(messaging, {
      vapidKey: VAPID_KEY,
      serviceWorkerRegistration: reg,
    })
    if (!token) return null

    cachedToken = token
    try {
      await registerDeviceToken(token)
    } catch {
      // Backend may 401 if the user just logged out — surface as null but
      // keep the token cached so an unregister-on-logout still works.
    }
    return token
  } catch {
    return null
  }
}

/**
 * Tear down the registration. Called on logout. Best-effort — we don't
 * deactivate the service worker (other tabs may still be using it).
 */
export async function unregisterCurrentPushToken() {
  const token = cachedToken
  if (!token) return
  cachedToken = null
  try {
    await unregisterDeviceToken(token)
  } catch {
    // Backend already gone or 401 — fine, the token will be evicted by
    // server-side TTL eventually.
  }
}

/**
 * Subscribe to foreground messages — fires while the tab is focused.
 * The service worker handles background delivery on its own.
 *
 * Returns an unsubscribe function (or a no-op when push isn't ready).
 */
export async function subscribeToForegroundPush(handler) {
  if (typeof handler !== 'function') return () => {}
  const messaging = await getMessagingIfReady()
  if (!messaging) return () => {}
  return onMessage(messaging, handler)
}
