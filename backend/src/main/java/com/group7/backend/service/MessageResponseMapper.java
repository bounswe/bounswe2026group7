package com.group7.backend.service;

import com.group7.backend.dto.response.AttachmentSummary;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.Message;
import org.springframework.stereotype.Component;

/**
 * Builds {@link MessageResponse} payloads from persisted {@link Message}
 * entities. Owning the {@link AttachmentUrlBuilder} dependency here keeps the
 * URL-rendering responsibility off the DTO and out of every service that
 * happens to return a message — services and the broadcast listener just
 * call {@link #toResponse(Message)}.
 */
@Component
public class MessageResponseMapper {

    private final AttachmentUrlBuilder urlBuilder;

    public MessageResponseMapper(AttachmentUrlBuilder urlBuilder) {
        this.urlBuilder = urlBuilder;
    }

    public MessageResponse toResponse(Message message) {
        MessageResponse r = new MessageResponse();
        r.setId(message.getId());

        Conversation conversation = message.getConversation();
        r.setConversationId(conversation.getId());
        if (conversation.getMentorship() != null) {
            r.setMentorshipId(conversation.getMentorship().getId());
        }

        r.setSenderId(message.getSender().getId());
        r.setSenderFirstName(message.getSender().getFirstName());
        r.setSenderLastName(message.getSender().getLastName());
        r.setContent(message.getContent());

        Attachment attachment = message.getAttachment();
        if (attachment != null) {
            r.setAttachment(AttachmentSummary.of(attachment, urlBuilder.downloadUrl(attachment.getId())));
        }

        r.setSentAt(message.getSentAt());
        r.setReadAt(message.getReadAt());
        return r;
    }
}
