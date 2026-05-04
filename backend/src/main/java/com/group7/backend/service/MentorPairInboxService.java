package com.group7.backend.service;

import com.group7.backend.dto.response.MentorPairInboxItem;
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
 * Read-side service that assembles a mentor's mentor-pair inbox: their
 * peer-mentor conversations with last-message preview and unread count,
 * paginated newest-first.
 *
 * <p>The assembly avoids N+1 by resolving the page once and then issuing
 * three bulk lookups (latest-message-per-conversation, unread-counts,
 * peers-by-id) keyed by the page's conversation ids. Total: five queries
 * (page + count + three bulk lookups) regardless of page size.
 *
 * <p><b>Invariant trusted by this service.</b> Every {@code MENTOR_PAIR}
 * row in {@code conversation_participants} has the matching user in
 * {@code pair_a_id} or {@code pair_b_id}. {@code ConversationCreator}
 * is the only writer of these rows and inserts both atomically; the
 * peer-id computation here ({@link #peerIdFor}) silently picks the wrong
 * user if a future writer breaks that assumption.
 */
@Service
public class MentorPairInboxService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MentorPairInboxService(ConversationRepository conversationRepository,
                                  MessageRepository messageRepository,
                                  UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<MentorPairInboxItem> listInbox(Long requesterId, Pageable pageable) {
        Page<Conversation> page = conversationRepository
                .findMentorPairConversationsForUserOrderedByLastMessage(requesterId, pageable);
        if (page.isEmpty()) {
            // Either the user has no pair conversations at all (totalElements
            // == 0) or the caller asked for a page beyond the last
            // (totalElements > 0, page content empty). In both cases skip the
            // helper batch loads — passing an empty collection to
            // `WHERE conversation_id IN :ids` on the native DISTINCT ON query
            // would make Postgres throw on `IN ()`. Preserve the page query's
            // own totalElements so the client can tell the two cases apart
            // (and learn they overshot when requesting a past-end page).
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

        List<MentorPairInboxItem> items = page.getContent().stream()
                .map(c -> toItem(c, requesterId, latestByConversation, unreadByConversation, peersById))
                .filter(Objects::nonNull)
                .toList();

        // Pass page.getTotalElements() through; Spring's PageImpl auto-corrects
        // the total to (offset + content.size()) when the requested window
        // overshoots the supplied total (i.e., on the last page) — so during a
        // peer-deletion race totalElements naturally drops to match the visible
        // content rather than being inflated by the dropped row. The next
        // request, after ON DELETE CASCADE on pair_a_id/pair_b_id has
        // propagated, sees a fully consistent state.
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    private static MentorPairInboxItem toItem(Conversation conversation,
                                              Long requesterId,
                                              Map<Long, ConversationLastMessageView> latestByConversation,
                                              Map<Long, Long> unreadByConversation,
                                              Map<Long, User> peersById) {
        Long peerId = peerIdFor(conversation, requesterId);
        User peer = peersById.get(peerId);
        if (peer == null) {
            // Race-defensive: peer's account was deleted between the page
            // query and the peer load. Drop this row rather than NPE on
            // peer.getFirstName(); ON DELETE CASCADE will remove the
            // conversation row by the time the client refetches.
            return null;
        }

        ConversationLastMessageView latest = latestByConversation.get(conversation.getId());

        MentorPairInboxItem item = new MentorPairInboxItem();
        item.setConversationId(conversation.getId());
        item.setPeerId(peerId);
        item.setPeerFirstName(peer.getFirstName());
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
