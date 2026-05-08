package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "meeting_reminder_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_meeting_reminder_offset",
                columnNames = {"meeting_id", "reminder_offset_minutes"}))
@Getter
@Setter
@NoArgsConstructor
public class MeetingReminderState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(name = "reminder_offset_minutes", nullable = false)
    private int reminderOffsetMinutes;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        this.sentAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
