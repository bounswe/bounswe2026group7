import { Client } from '@stomp/stompjs'

/**
 * Singleton STOMP client over the backend's `/ws/chat` endpoint.
 *
 * Backend contract (see backend/.../JwtChannelInterceptor.java + WebSocketConfig.java):
 *  - The CONNECT frame must carry the JWT in the `Authorization` STOMP native header
 *    as `Bearer <token>` — never via URL.
 *  - Subscriptions to `/topic/conversation/{id}` are auth-checked server-side; the
 *    interceptor returns an ERROR frame for non-participants.
 *  - The simple in-memory broker on `/topic` does not require a SockJS shim.
 *
 * This module exposes two operations:
 *  - `getStompClient()` — returns a connected (or connecting) singleton, lazily
 *    constructed on first call. Subsequent calls reuse the same client.
 *  - `disconnectStomp()` — tears the client down on logout / sign-out.
 *
 * Subscription management is the caller's responsibility (see
 * `useConversationSubscription`). The client survives unmounts of individual
 * pages so navigating between conversations doesn't churn the socket.
 */

const WS_URL = (() => {
  // Vite dev server proxies /ws to the backend, but the STOMP client needs an
  // absolute ws:// or wss:// URL. Derive it from the current origin.
  if (typeof window === 'undefined') return 'ws://localhost:8080/ws/chat'
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws/chat`
})()

let client = null

function buildClient() {
  const token = localStorage.getItem('auth_token')
  const c = new Client({
    brokerURL: WS_URL,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // Quiet by default; flip on for STOMP frame debugging
    debug: () => {},
  })
  return c
}

/**
 * Returns the singleton STOMP Client. Activates it on first call. Callers
 * should subscribe via `client.subscribe(...)` once `client.connected === true`,
 * but `Client.subscribe` queues subscriptions until the underlying socket is
 * ready, so callers usually do not need to wait explicitly.
 */
export function getStompClient() {
  if (!client) {
    client = buildClient()
    client.activate()
  }
  return client
}

/**
 * Tear the singleton down (e.g. on logout). Idempotent.
 */
export function disconnectStomp() {
  if (client) {
    try { client.deactivate() } catch { /* ignore */ }
    client = null
  }
}
