import { useEffect, useRef } from 'react'
import { getStompClient } from '../services/stompClient'

/**
 * Subscribe to live messages on `/topic/conversation/{conversationId}`.
 *
 * Backend (MessageBroadcastListener) publishes a serialized MessageResponse to
 * this topic AFTER each message-send transaction commits. The hook invokes
 * `onMessage(messageResponse)` for every frame received.
 *
 * Lifecycle:
 *  - On mount (or conversationId change): activates the singleton client (if not
 *    already active) and subscribes to the topic.
 *  - On unmount (or conversationId change): unsubscribes. The client itself stays
 *    alive so re-mounting elsewhere reuses the existing socket.
 *  - When `conversationId` is null/undefined, the hook does nothing.
 *
 * The latest `onMessage` is stored in a ref so subscribers don't need to memoize
 * their callback to avoid resubscribe churn.
 */
export default function useConversationSubscription(conversationId, onMessage) {
  const handlerRef = useRef(onMessage)
  handlerRef.current = onMessage

  useEffect(() => {
    if (!conversationId) return undefined

    const client = getStompClient()
    let subscription = null

    function attach() {
      try {
        subscription = client.subscribe(
          `/topic/conversation/${conversationId}`,
          (frame) => {
            try {
              const payload = JSON.parse(frame.body)
              handlerRef.current?.(payload)
            } catch {
              // Ignore malformed frames — broadcast layer always sends JSON
            }
          },
        )
      } catch {
        // Subscribe can throw if the client is mid-deactivation; the
        // reconnect-and-retry path below handles this.
      }
    }

    if (client.connected) {
      attach()
    } else {
      // Defer subscription until CONNECT completes
      const original = client.onConnect
      client.onConnect = (frame) => {
        try { original?.(frame) } catch { /* ignore */ }
        attach()
      }
    }

    return () => {
      try { subscription?.unsubscribe() } catch { /* ignore */ }
    }
  }, [conversationId])
}
