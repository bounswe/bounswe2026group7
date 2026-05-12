import { useEffect, useRef } from 'react'
import { getStompClient } from '../services/stompClient'

/**
 * Subscribe to live feed pushes on `/topic/feed.{userId}` (#349).
 *
 * Backend (FeedFanoutListener) publishes two payload shapes to this topic
 * after a post or share commits in the viewer's follow graph:
 *   - FeedPostPushPayload  : { postId, authorId, authorFirstName, createdAt }
 *   - FeedSharePushPayload : { shareId, postId, sharerId, sharerFirstName,
 *                              commentary, sharedAt }
 *
 * The two shapes are distinguished by presence of `sharerId` (share) vs.
 * `authorId` (original post). Callers pass `onPost` and / or `onShare`;
 * frames are routed by the discriminator without the caller having to
 * write the JSON.parse + try/catch boilerplate.
 *
 * Lifecycle mirrors `useConversationSubscription` — the singleton STOMP
 * client survives unmounts so navigating between pages doesn't churn the
 * socket. When `userId` is null / undefined the hook is a no-op.
 *
 * The latest callbacks are stored in refs so callers don't need to memoize.
 */
export default function useFeedSubscription(userId, { onPost, onShare } = {}) {
  const postRef = useRef(onPost)
  const shareRef = useRef(onShare)
  postRef.current = onPost
  shareRef.current = onShare

  useEffect(() => {
    if (!userId) return undefined
    const client = getStompClient()
    let subscription = null

    function attach() {
      try {
        subscription = client.subscribe(
          `/topic/feed.${userId}`,
          (frame) => {
            try {
              const payload = JSON.parse(frame.body)
              if (payload.sharerId != null) {
                shareRef.current?.(payload)
              } else {
                postRef.current?.(payload)
              }
            } catch {
              // Backend always sends JSON; bad frames are ignored.
            }
          },
        )
      } catch {
        // Subscribe can throw mid-deactivate; the reconnect path retries.
      }
    }

    if (client.connected) {
      attach()
    } else {
      const original = client.onConnect
      client.onConnect = (frame) => {
        try { original?.(frame) } catch { /* ignore */ }
        attach()
      }
    }

    return () => {
      try { subscription?.unsubscribe() } catch { /* ignore */ }
    }
  }, [userId])
}
