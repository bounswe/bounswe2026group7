package com.group7.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String profilePhoto;

    @Column(nullable = false)
    private Boolean isEmailVerified = false;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    /**
     * Optional human-readable city (e.g. "Istanbul"). Free-form to avoid a
     * gazetteer dependency; the proximity signal uses case-insensitive
     * equality for the "same city" fallback when coordinates are absent.
     * See {@code V34__add_user_location.sql} for the schema rationale.
     */
    @Column(length = 120)
    private String city;

    /**
     * Optional latitude in decimal degrees (-90 to 90). NULL when the user
     * hasn't set coordinates. CHECK constraint at the DB level rejects out-
     * of-range values; pair-completeness constraint ensures both lat+lon
     * are either present together or both absent.
     */
    private Double latitude;

    /**
     * Optional longitude in decimal degrees (-180 to 180). See {@link #latitude}.
     */
    private Double longitude;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}

