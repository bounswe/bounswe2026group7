package com.group7.backend.config.jsonld;

import com.group7.backend.config.AppProperties;
import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.dto.response.MessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoteMappingTest {

    private NoteMapping mapping;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("http://localhost:8080");
        mapping = new NoteMapping(props);
    }

    @Test
    void supportsOnlyMessageResponse() {
        assertThat(mapping.supports(MessageResponse.class)).isTrue();
        assertThat(mapping.supports(String.class)).isFalse();
    }

    @Test
    void emitsCreateActivityWithFullActorAndNote() {
        MessageResponse m = baseMessage();
        m.setSenderFirstName("Mira");
        m.setSenderLastName("Mentor");
        m.setContent("Hello");

        Map<String, Object> result = mapping.apply(m);

        assertThat(result.get("@type")).isEqualTo("Create");
        assertThat(result.get("@id")).isEqualTo(
                "http://localhost:8080/api/conversations/900/messages/500#create");
        assertThat(result.get("published")).isNotNull();

        Map<?, ?> actor = (Map<?, ?>) result.get("actor");
        assertThat(actor.get("@type")).isEqualTo("Person");
        assertThat(actor.get("@id")).isEqualTo("http://localhost:8080/api/users/7");
        assertThat(actor.get("name")).isEqualTo("Mira Mentor");

        Map<?, ?> note = (Map<?, ?>) result.get("object");
        assertThat(note.get("@type")).isEqualTo("Note");
        assertThat(note.get("content")).isEqualTo("Hello");
        assertThat(note.get("attributedTo")).isEqualTo("http://localhost:8080/api/users/7");
        assertThat(note.get("attachment")).isNull();
    }

    @Test
    void embedsAttachmentBlockWhenAttachmentSummaryPresent() {
        MessageResponse m = baseMessage();
        AttachmentSummary summary = new AttachmentSummary();
        summary.setId(UUID.randomUUID());
        summary.setDownloadUrl("http://localhost:8080/api/uploads/attachments/abc");
        summary.setFilename("abc.pdf");
        summary.setContentType("application/pdf");
        summary.setSizeBytes(123);
        m.setAttachment(summary);

        Map<String, Object> result = mapping.apply(m);
        Map<?, ?> note = (Map<?, ?>) result.get("object");
        Map<?, ?> attachment = (Map<?, ?>) note.get("attachment");

        assertThat(attachment.get("@type")).isEqualTo("Document");
        assertThat(attachment.get("url")).isEqualTo(summary.getDownloadUrl());
        assertThat(attachment.get("mediaType")).isEqualTo("application/pdf");
    }

    @Test
    void omitsMediaTypeWhenAttachmentHasNoContentType() {
        MessageResponse m = baseMessage();
        AttachmentSummary summary = new AttachmentSummary();
        summary.setId(UUID.randomUUID());
        summary.setDownloadUrl("http://localhost:8080/api/uploads/attachments/abc");
        // contentType deliberately null
        m.setAttachment(summary);

        Map<String, Object> result = mapping.apply(m);
        Map<?, ?> attachment = (Map<?, ?>) ((Map<?, ?>) result.get("object")).get("attachment");
        assertThat(attachment.get("mediaType")).isNull();
        assertThat(attachment.get("url")).isEqualTo(summary.getDownloadUrl());
    }

    @Test
    void omitsAttachmentBlockWhenDownloadUrlMissing() {
        // Defensive: if for some reason a summary lands without the URL having
        // been computed, we still emit a well-formed Note rather than a
        // broken attachment link.
        MessageResponse m = baseMessage();
        AttachmentSummary summary = new AttachmentSummary();
        summary.setId(UUID.randomUUID());
        // downloadUrl deliberately null
        m.setAttachment(summary);

        Map<String, Object> result = mapping.apply(m);
        Map<?, ?> note = (Map<?, ?>) result.get("object");
        assertThat(note.get("attachment")).isNull();
    }

    @Test
    void omitsBlankContent() {
        MessageResponse m = baseMessage();
        m.setContent("   ");

        Map<String, Object> result = mapping.apply(m);
        Map<?, ?> note = (Map<?, ?>) result.get("object");
        assertThat(note.get("content")).isNull();
    }

    @Test
    void buildsActorWithFirstNameOnlyWhenLastNameMissing() {
        MessageResponse m = baseMessage();
        m.setSenderFirstName("Mira");
        m.setSenderLastName(null);
        Map<?, ?> actor = (Map<?, ?>) mapping.apply(m).get("actor");
        assertThat(actor.get("name")).isEqualTo("Mira");
    }

    @Test
    void buildsActorWithLastNameOnlyWhenFirstNameMissing() {
        MessageResponse m = baseMessage();
        m.setSenderFirstName(null);
        m.setSenderLastName("Mentor");
        Map<?, ?> actor = (Map<?, ?>) mapping.apply(m).get("actor");
        assertThat(actor.get("name")).isEqualTo("Mentor");
    }

    @Test
    void omitsActorNameWhenBothNamesMissing() {
        MessageResponse m = baseMessage();
        m.setSenderFirstName(null);
        m.setSenderLastName(null);
        Map<?, ?> actor = (Map<?, ?>) mapping.apply(m).get("actor");
        assertThat(actor.containsKey("name")).isFalse();
    }

    @Test
    void omitsActorIdWhenSenderIdMissing() {
        MessageResponse m = baseMessage();
        m.setSenderId(null);
        Map<String, Object> result = mapping.apply(m);
        Map<?, ?> actor = (Map<?, ?>) result.get("actor");
        assertThat(actor.containsKey("@id")).isFalse();
        Map<?, ?> note = (Map<?, ?>) result.get("object");
        assertThat(note.containsKey("attributedTo")).isFalse();
    }

    @Test
    void omitsTopLevelIdAndPublishedWhenMessageIdAndSentAtMissing() {
        MessageResponse m = baseMessage();
        m.setId(null);
        m.setSentAt(null);
        Map<String, Object> result = mapping.apply(m);
        assertThat(result.containsKey("@id")).isFalse();
        assertThat(result.containsKey("published")).isFalse();
        Map<?, ?> note = (Map<?, ?>) result.get("object");
        assertThat(note.containsKey("published")).isFalse();
    }

    @Test
    void stripsTrailingSlashFromBaseUrl() {
        AppProperties props = new AppProperties();
        props.setBaseUrl("http://h:1/");
        NoteMapping m = new NoteMapping(props);

        Map<String, Object> result = m.apply(baseMessage());
        assertThat(result.get("@id")).asString().startsWith("http://h:1/api/conversations/");
    }

    private MessageResponse baseMessage() {
        MessageResponse m = new MessageResponse();
        m.setId(500L);
        m.setConversationId(900L);
        m.setSenderId(7L);
        m.setSentAt(OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC));
        return m;
    }
}
