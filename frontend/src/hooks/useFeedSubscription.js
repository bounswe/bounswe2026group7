import { useEffect, useRef } from 'react'
import { getStompClient } from '../services/stompClient'

/**
 * Subscribe to live feed pushes on `/topic/feed.{userId}` (#349).
 *
 * Backend (FeedFanoutListener) publishes three payload shapes to this
 * topic, all routed to followers of the relevant author/sharer:
 *   - FeedPostPushPayload       : { postId, authorId, authorFirstName, createdAt }
 *   - FeedSharePushPayload      : { shareId, postId, sharerId, sharerFirstName,
 *                                   commentary, sharedAt }
 *   - FeedEngagementPushPayload : { postId, likeCount, commentCount, shareCount,
 *                                   updatedAt }  -- has neither authorId nor sharerId
 *
 * Callers pass `onPost`, `onShare`, and/or `onEngagement`; frames are
 * routed by the discriminator without the caller writing JSON.parse +
 * try/catch boilerplate.
 *
 * Lifecycle mirrors `useConversationSubscription` — the singleton STOMP
 * client survives unmounts so navigating between pages doesn't churn the
 * socket. When `userId` is null / undefined the hook is a no-op.
 *
 * The latest callbacks are stored in refs so callers don't need to memoize.
 */
export default function useFeedSubscription(userId, { onPost, onShare, onEngagement } = {}) {
  const postRef = useRef(onPost)
  const shareRef = useRef(onShare)
  const engagementRef = useRef(onEngagement)
  postRef.current = onPost
  shareRef.current = onShare
  engagementRef.current = onEngagement

  useEffect(() => {
    if (!userId) return undefined
    const client = getStompClient()
    let subscription = null
    // `cancelled` guards the deferred-CONNECT path: if the hook unmounts
    // before the socket connects, the attach() scheduled on onConnect must
    // short-circuit, otherwise we'd subscribe with no cleanup reference and
    // leak a zombie subscription on every reconnect.
    let cancelled = false

    function attach() {
      if (cancelled) return
      try {
        subscription = client.subscribe(
          `/topic/feed.${userId}`,
          (frame) => {
            try {
              const payload = JSON.parse(frame.body)
              if (payload.sharerId != null) {
                shareRef.current?.(payload)
              } else if (payload.authorId != null) {
                postRef.current?.(payload)
              } else if (payload.postId != null) {
                // Engagement payload — has neither authorId nor sharerId.
                engagementRef.current?.(payload)
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
      // Defer subscription until CONNECT completes. Chain onto any prior
      // override so multiple concurrent hooks all get their attach() call.
      const original = client.onConnect
      client.onConnect = (frame) => {
        try { original?.(frame) } catch { /* ignore */ }
        attach()
      }
    }

    return () => {
      cancelled = true
      try { subscription?.unsubscribe() } catch { /* ignore */ }
    }
  }, [userId])
}
