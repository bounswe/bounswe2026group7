package com.group7.backend.service;

/**
 * Outcome of a {@code FollowService.follow(...)} call. The {@code created}
 * flag distinguishes a freshly-inserted edge ({@code true} → controller
 * returns HTTP {@code 201}) from a duplicate POST that found an existing
 * edge ({@code false} → HTTP {@code 200}). Carries no {@code createdAt}:
 * the frontend never needs the timestamp for the follow toggle, and
 * surfacing it here would force a same-transaction read after the native
 * upsert that interacts unpleasantly with Hibernate's L1 cache.
 */
public record FollowResult(Long followerId, Long followeeId, boolean created) {
}
