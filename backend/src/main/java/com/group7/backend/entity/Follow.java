package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A directional follow edge: {@code follower → followee}. No acceptance step;
 * Twitter-style, not Facebook-style. The model is intentionally thin (no
 * {@code @ManyToOne} refs to {@link User}) so that paged list reads do not
 * trigger N+1 lazy-load queries and so the native {@code INSERT ... ON
 * CONFLICT DO NOTHING} upsert in
 * {@link com.group7.backend.repository.FollowRepository#upsertFollow}
 * doesn't have to coordinate with Hibernate's {@code @MapsId} dance.
 *
 * <p>{@code created_at} is DB-managed via the migration's {@code DEFAULT
 * NOW()} clause. The JPA mapping marks it {@code insertable = false} so any
 * stray {@code save()} call on a freshly constructed {@link Follow} won't
 * try to write a Java-side timestamp; the row's timestamp comes from the
 * database. There is no {@code @PrePersist} on this entity — the upsert
 * bypasses lifecycle callbacks anyway, and a duplicate Java-side default
 * would only invite UTC-vs-local-time discrepancies.
 *
 * <p>Spec for #343 covers follow / unfollow only; blocking and audit-trail
 * concerns are out of scope and would land as a parallel {@code blocks}
 * table rather than a {@code connection_type} column on this one.
 */
@Entity
@Table(name = "follows")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Follow {

    @EmbeddedId
    private FollowId id;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}
