import { useEffect, useRef } from 'react'
import { useAuth } from '../context/AuthContext'
import {
  isPushSupported,
  isPushConfigured,
  ensurePushRegistered,
  unregisterCurrentPushToken,
  subscribeToForegroundPush,
} from '../services/pushNotifications'
import { showTransientToast } from '../utils/toast'

/**
 * Wire web push (#447) into the app lifecycle:
 *  - Registers a service worker + obtains an FCM token after login.
 *  - Subscribes to foreground messages and surfaces them as toasts +
 *    fires a `notifications:refresh` window event so NotificationBell
 *    refetches its unread badge.
 *  - Unregisters the token on logout.
 *
 * Mounted once at the root via <PushBootstrapper /> below. Safe to call
 * with no Firebase env vars — all underlying push primitives are
 * feature-gated and degrade to no-ops cleanly.
 */
export default function usePushNotifications() {
  const { token: authToken } = useAuth()
  const tokenRef = useRef(authToken)
  const unsubscribeRef = useRef(null)

  useEffect(() => {
    const previousAuth = tokenRef.current
    tokenRef.current = authToken
    let cancelled = false

    // Logged out — tear down.
    if (!authToken) {
      if (previousAuth) {
        unregisterCurrentPushToken().catch(() => {})
      }
      if (unsubscribeRef.current) {
        try { unsubscribeRef.current() } catch { /* ignore */ }
        unsubscribeRef.current = null
      }
      return undefined
    }

    // Logged in — register and subscribe to foreground.
    if (!isPushSupported() || !isPushConfigured()) return undefined

    ensurePushRegistered()
      .then(async (regToken) => {
        if (cancelled || !regToken) return
        const unsub = await subscribeToForegroundPush((payload) => {
          // Toast for the in-app surface; system tray stays empty when the
          // tab is focused (browser convention).
          const title = payload?.notification?.title || 'New notification'
          showTransientToast(title)
          // Nudge the bell to refetch. Components subscribed to this event
          // (NotificationBell) will re-run getNotifications.
          window.dispatchEvent(new CustomEvent('notifications:refresh'))
        })
        if (typeof unsub === 'function') {
          unsubscribeRef.current = unsub
        }
      })
      .catch(() => { /* swallow — push is a nice-to-have */ })

    return () => {
      cancelled = true
    }
  }, [authToken])
}
