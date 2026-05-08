package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Per-user cursor: the timestamp at which {@code userId} last marked the
 * social feed read (#349). Powers {@code GET /api/feed/unread-count} —
 * count posts in the user's follow graph created after this timestamp.
 *
 * <p><b>No {@code @Version} column.</b> This entity is mutated exclusively
 * via the native {@code INSERT ... ON CONFLICT DO UPDATE} in
 * {@link com.group7.backend.repository.LastFeedReadAtRepository#markRead},
 * which bypasses JPA optimistic locking. Adding {@code @Version} would be
 * dead code (the upsert never triggers the version check). Mirrors the
 * {@link Follow} (#343) precedent for native-upsert-only entities, in
 * deliberate contrast to {@link LastMatchNotification} (#273) which is
 * mutated via {@code save()} and therefore needs {@code @Version}.
 */
@Entity
@Table(name = "last_feed_read_at")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LastFeedReadAt {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "last_read_at", nullable = false)
    private OffsetDateTime lastReadAt;
}
