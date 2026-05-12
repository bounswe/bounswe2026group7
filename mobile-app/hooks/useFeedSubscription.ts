import { useEffect, useRef } from 'react';
import { getStompClient } from '../services/stompClient';

type FeedPostPush = {
  postId: number;
  authorId: number;
  authorFirstName: string;
  createdAt: string;
};

type FeedSharePush = {
  shareId: number;
  postId: number;
  sharerId: number;
  sharerFirstName: string;
  commentary: string | null;
  sharedAt: string;
};

type Callbacks = {
  onPost?: (payload: FeedPostPush) => void;
  onShare?: (payload: FeedSharePush) => void;
};

/**
 * Subscribes to `/topic/feed.{userId}` via STOMP and routes incoming frames
 * to `onPost` or `onShare` based on payload shape (sharerId present = share).
 * Mirrors the web `useFeedSubscription` hook. No-op when `userId` is null.
 */
export default function useFeedSubscription(userId: number | null, { onPost, onShare }: Callbacks = {}) {
  const postRef = useRef(onPost);
  const shareRef = useRef(onShare);
  postRef.current = onPost;
  shareRef.current = onShare;

  useEffect(() => {
    if (!userId) return;

    let subscription: { unsubscribe: () => void } | null = null;
    let cancelled = false;

    getStompClient().then((stompClient) => {
      if (cancelled) return;

      function attach() {
        if (cancelled) return;
        try {
          subscription = stompClient.subscribe(
            `/topic/feed.${userId}`,
            (frame) => {
              try {
                const payload = JSON.parse(frame.body);
                if (payload.sharerId != null) {
                  shareRef.current?.(payload as FeedSharePush);
                } else {
                  postRef.current?.(payload as FeedPostPush);
                }
              } catch {
                // malformed frame — ignore
              }
            },
          );
        } catch {
          // subscribe can throw mid-deactivate
        }
      }

      if (stompClient.connected) {
        attach();
      } else {
        const original = stompClient.onConnect;
        stompClient.onConnect = (frame) => {
          try { original?.(frame); } catch { /* ignore */ }
          attach();
        };
      }
    });

    return () => {
      cancelled = true;
      try { subscription?.unsubscribe(); } catch { /* ignore */ }
    };
  }, [userId]);
}
