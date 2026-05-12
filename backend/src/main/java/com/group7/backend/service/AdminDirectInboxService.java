package com.group7.backend.service;

import com.group7.backend.dto.response.AdminDirectInboxItem;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.User;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MessageRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.projection.ConversationLastMessageView;
import com.group7.backend.repository.projection.ConversationUnreadCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-side service that assembles a user's ADMIN_DIRECT inbox with preview
 * and unread count, newest-first.
 */
@Service
public class AdminDirectInboxService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public AdminDirectInboxService(ConversationRepository conversationRepository,
                                   MessageRepository messageRepository,
                                   UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminDirectInboxItem> listInbox(Long requesterId, Pageable pageable) {
        Page<Conversation> page = conversationRepository
                .findAdminDirectConversationsForUserOrderedByLastMessage(requesterId, pageable);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        }

        List<Long> conversationIds = page.getContent().stream()
                .map(Conversation::getId)
                .toList();

        Map<Long, ConversationLastMessageView> latestByConversation = messageRepository
                .findLatestPerConversation(conversationIds).stream()
                .collect(Collectors.toMap(
                        ConversationLastMessageView::getConversationId,
                        Function.identity()));

        Map<Long, Long> unreadByConversation = messageRepository
                .countUnreadPerConversationForReader(conversationIds, requesterId).stream()
                .collect(Collectors.toMap(
                        ConversationUnreadCount::getConversationId,
                        ConversationUnreadCount::getUnreadCount));

        List<Long> peerIds = page.getContent().stream()
                .map(c -> peerIdFor(c, requesterId))
                .toList();

        Map<Long, User> peersById = userRepository.findAllById(peerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminDirectInboxItem> items = page.getContent().stream()
                .map(c -> toItem(c, requesterId, latestByConversation, unreadByConversation, peersById))
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    private static AdminDirectInboxItem toItem(Conversation conversation,
                                               Long requesterId,
                                               Map<Long, ConversationLastMessageView> latestByConversation,
                                               Map<Long, Long> unreadByConversation,
                                               Map<Long, User> peersById) {
        Long peerId = peerIdFor(conversation, requesterId);
        User peer = peersById.get(peerId);
        if (peer == null) {
            return null;
        }

        ConversationLastMessageView latest = latestByConversation.get(conversation.getId());

        AdminDirectInboxItem item = new AdminDirectInboxItem();
        item.setConversationId(conversation.getId());
        item.setPeerId(peerId);
        item.setPeerFirstName(peer.getFirstName());
        item.setPeerLastName(peer.getLastName());
        item.setLastMessageContent(latest != null ? latest.getContent() : null);
        item.setLastMessageSentAt(latest != null
                ? OffsetDateTime.ofInstant(latest.getSentAt(), ZoneOffset.UTC)
                : null);
        item.setUnreadCount(unreadByConversation.getOrDefault(conversation.getId(), 0L));
        return item;
    }

    private static Long peerIdFor(Conversation conversation, Long requesterId) {
        return conversation.getPairAId().equals(requesterId)
                ? conversation.getPairBId()
                : conversation.getPairAId();
    }
}
