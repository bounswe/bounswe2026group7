package com.group7.backend.repository.projection;

import java.time.Instant;

/**
 * Spring Data interface projection for the latest message of a conversation.
 * Returned by {@code MessageRepository.findLatestPerConversation} so the inbox
 * can build its preview without round-tripping a managed {@code Message}
 * entity (which would lazy-load {@code Message.getConversation()} during DTO
 * assembly).
 *
 * <p>Lives in {@code repository/projection/} rather than {@code dto/response/}
 * because this is an internal data carrier — never serialised to clients.
 *
 * <p>{@code getSentAt()} returns {@link Instant} rather than
 * {@code OffsetDateTime} because Hibernate maps Postgres
 * {@code timestamp with time zone} columns to {@code Instant} for native
 * queries (no Converter is registered for Instant → OffsetDateTime). The
 * service converts to {@link java.time.OffsetDateTime} at the DTO boundary.
 */
public interface ConversationLastMessageView {

    Long getConversationId();

    String getContent();

    Instant getSentAt();
}
