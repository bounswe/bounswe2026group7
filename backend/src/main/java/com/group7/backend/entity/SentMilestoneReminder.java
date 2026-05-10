package com.group7.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "sent_milestone_reminders",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "milestone_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class SentMilestoneReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "milestone_id", nullable = false)
    private Long milestoneId;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;
}
