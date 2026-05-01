package com.group7.backend.entity;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the {@code @PrePersist} hooks across messaging entities. JPA
 * normally invokes these via the EntityManager; in unit tests we drive them
 * directly to cover both the "field already set" and "field defaulted here"
 * branches without spinning up a database.
 */
class EntityPrePersistTest {

    @Test
    void attachmentOnCreate_assignsIdAndCreatedAtWhenAbsent() {
        Attachment a = new Attachment();
        ReflectionTestUtils.invokeMethod(a, "onCreate");
        assertThat(a.getId()).isNotNull();
        assertThat(a.getCreatedAt()).isNotNull();
    }

    @Test
    void attachmentOnCreate_preservesPreSetValues() {
        Attachment a = new Attachment();
        UUID preset = UUID.randomUUID();
        OffsetDateTime fixed = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        a.setId(preset);
        a.setCreatedAt(fixed);
        ReflectionTestUtils.invokeMethod(a, "onCreate");
        assertThat(a.getId()).isEqualTo(preset);
        assertThat(a.getCreatedAt()).isEqualTo(fixed);
    }

    @Test
    void messageOnCreate_assignsSentAtWhenAbsent() {
        Message m = new Message();
        ReflectionTestUtils.invokeMethod(m, "onCreate");
        assertThat(m.getSentAt()).isNotNull();
    }

    @Test
    void messageOnCreate_preservesPreSetSentAt() {
        Message m = new Message();
        OffsetDateTime fixed = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        m.setSentAt(fixed);
        ReflectionTestUtils.invokeMethod(m, "onCreate");
        assertThat(m.getSentAt()).isEqualTo(fixed);
    }

    @Test
    void conversationOnCreate_assignsCreatedAtWhenAbsent() {
        Conversation c = new Conversation();
        ReflectionTestUtils.invokeMethod(c, "onCreate");
        assertThat(c.getCreatedAt()).isNotNull();
    }

    @Test
    void conversationOnCreate_preservesPreSetCreatedAt() {
        Conversation c = new Conversation();
        OffsetDateTime fixed = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        c.setCreatedAt(fixed);
        ReflectionTestUtils.invokeMethod(c, "onCreate");
        assertThat(c.getCreatedAt()).isEqualTo(fixed);
    }

    @Test
    void conversationParticipantOnCreate_assignsJoinedAtAndCompositeIdFromRefs() {
        ConversationParticipant cp = new ConversationParticipant();
        Conversation c = new Conversation();
        c.setId(900L);
        Mentor user = new Mentor();
        user.setId(7L);
        cp.setConversation(c);
        cp.setUser(user);
        ReflectionTestUtils.invokeMethod(cp, "onCreate");
        assertThat(cp.getJoinedAt()).isNotNull();
        assertThat(cp.getId()).isNotNull();
        assertThat(cp.getId().getConversationId()).isEqualTo(900L);
        assertThat(cp.getId().getUserId()).isEqualTo(7L);
    }

    @Test
    void conversationParticipantOnCreate_preservesPreSetIdAndJoinedAt() {
        ConversationParticipant cp = new ConversationParticipant();
        ConversationParticipantId presetId = new ConversationParticipantId(900L, 7L);
        OffsetDateTime fixed = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        cp.setId(presetId);
        cp.setJoinedAt(fixed);
        ReflectionTestUtils.invokeMethod(cp, "onCreate");
        assertThat(cp.getId()).isSameAs(presetId);
        assertThat(cp.getJoinedAt()).isEqualTo(fixed);
    }

    @Test
    void conversationParticipantOnCreate_skipsIdInferenceWhenConversationMissing() {
        ConversationParticipant cp = new ConversationParticipant();
        Mentor user = new Mentor();
        user.setId(7L);
        cp.setUser(user);
        // conversation deliberately null
        ReflectionTestUtils.invokeMethod(cp, "onCreate");
        assertThat(cp.getId()).isNull();
        assertThat(cp.getJoinedAt()).isNotNull();
    }

    @Test
    void conversationParticipantOnCreate_skipsIdInferenceWhenUserMissing() {
        ConversationParticipant cp = new ConversationParticipant();
        Conversation c = new Conversation();
        c.setId(900L);
        cp.setConversation(c);
        // user deliberately null
        ReflectionTestUtils.invokeMethod(cp, "onCreate");
        assertThat(cp.getId()).isNull();
        assertThat(cp.getJoinedAt()).isNotNull();
    }
}
