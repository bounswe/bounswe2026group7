package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A messaging thread. The {@code kind} discriminator + optional {@code mentorship}
 * FK determine which creation policy was used; the {@code participants} junction
 * carries the actual user identities.
 *
 * <p>Mentorship-scoped conversations have a non-null {@link #mentorship}; mentor-pair
 * (and any future kind) leave it null. A partial unique index on {@code mentorship_id}
 * (defined in V14) enforces "at most one conversation per mentorship".
 */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentorship_id")
    private Mentorship mentorship;

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ConversationParticipant> participants = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
