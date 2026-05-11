package com.group7.backend.event;

/**
 * Published after a {@link com.group7.backend.entity.Follow} edge is
 * inserted or removed in Postgres (#437). The Neo4j graph mirror listens
 * via {@code @TransactionalEventListener(AFTER_COMMIT)} so a Neo4j
 * outage cannot abort the Postgres transaction.
 *
 * <p>Fields are primitive ids — no entity references — so the listener
 * can run on a different transaction (or no transaction) without lazy-
 * initialisation hazards.
 */
public record FollowChangedEvent(
        Long followerId,
        Long followeeId,
        ChangeType type
) {
    public enum ChangeType {
        FOLLOWED,
        UNFOLLOWED
    }

    public static FollowChangedEvent followed(Long followerId, Long followeeId) {
        return new FollowChangedEvent(followerId, followeeId, ChangeType.FOLLOWED);
    }

    public static FollowChangedEvent unfollowed(Long followerId, Long followeeId) {
        return new FollowChangedEvent(followerId, followeeId, ChangeType.UNFOLLOWED);
    }
}
