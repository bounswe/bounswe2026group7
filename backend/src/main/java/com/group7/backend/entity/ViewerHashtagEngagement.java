package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Per-viewer, per-hashtag Beta(α, β) posterior state for Thompson-sampling
 * exploration in the advanced For-You feed ranker.
 *
 * <p>Rows are created lazily on the first engagement (initial α=2.0 = 1.0
 * prior + 1.0 engagement); subsequent engagements increment α atomically
 * via a native UPSERT on
 * {@link com.group7.backend.repository.ViewerHashtagEngagementRepository}.
 * β stays at 1.0 in v1 — the negative-signal update path lands with the
 * impression-tracking follow-up PR.
 *
 * <p>The composite PK {@code (userId, hashtag)} is sufficient for both the
 * single-row PK lookup ({@code samplePosterior}) and the per-viewer prefix
 * scan; deliberately no secondary index so Postgres can preserve HOT
 * updates on the α-increment path.
 */
@Entity
@Table(name = "viewer_hashtag_engagement")
@IdClass(ViewerHashtagEngagement.Id.class)
public class ViewerHashtagEngagement {

    @jakarta.persistence.Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "hashtag", nullable = false, length = 50)
    private String hashtag;

    @Column(name = "alpha", nullable = false)
    private double alpha;

    @Column(name = "beta", nullable = false)
    private double beta;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private OffsetDateTime lastUpdated;

    protected ViewerHashtagEngagement() {
    }

    public ViewerHashtagEngagement(Long userId, String hashtag, double alpha, double beta) {
        this.userId = userId;
        this.hashtag = hashtag;
        this.alpha = alpha;
        this.beta = beta;
    }

    public Long getUserId() { return userId; }
    public String getHashtag() { return hashtag; }
    public double getAlpha() { return alpha; }
    public double getBeta() { return beta; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getLastUpdated() { return lastUpdated; }

    public void setAlpha(double alpha) { this.alpha = alpha; }
    public void setBeta(double beta) { this.beta = beta; }

    /** Composite primary key for {@link ViewerHashtagEngagement}. */
    public static class Id implements Serializable {
        private Long userId;
        private String hashtag;

        public Id() {}
        public Id(Long userId, String hashtag) {
            this.userId = userId;
            this.hashtag = hashtag;
        }

        public Long getUserId() { return userId; }
        public String getHashtag() { return hashtag; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id other)) return false;
            return Objects.equals(userId, other.userId) && Objects.equals(hashtag, other.hashtag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, hashtag);
        }
    }
}
