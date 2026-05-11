package com.group7.backend.service;

import com.group7.backend.dto.response.MentorPairInboxItem;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MessageRepository;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.projection.ConversationLastMessageView;
import com.group7.backend.repository.projection.ConversationUnreadCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of {@link MentorPairInboxService}'s assembly logic.
 * The repositories are mocked; this suite pins the service's mapping rules
 * (peer-id ternary, latest-message lookup, unread default-zero, race-defensive
 * null-peer filter, totalElements preservation) so a future refactor catches
 * regressions without needing the integration suite to spot them.
 *
 * <p>The native {@code findLatestPerConversation} query itself is exercised
 * end-to-end against real Postgres in {@code MentorPairInboxIntegrationTest};
 * H2's lack of {@code DISTINCT ON} support keeps it out of {@code @DataJpaTest}.
 */
@ExtendWith(MockitoExtension.class)
class MentorPairInboxServiceTest {

    private static final Long REQUESTER_ID = 100L;

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;

    private MentorPairInboxService inboxService;

    @BeforeEach
    void setUp() {
        inboxService = new MentorPairInboxService(
                conversationRepository, messageRepository, userRepository);
    }

    // ── 1. Empty page short-circuits the helper queries ────────────────────

    @Test
    void emptyInbox_returnsEmptyPageWithoutBatchLoads() {
        Pageable pageable = PageRequest.of(0, 20);
        when(conversationRepository.findMentorPairConversationsForUserOrderedByLastMessage(
                eq(REQUESTER_ID), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<MentorPairInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(messageRepository, never()).findLatestPerConversation(anyCollection());
        verify(messageRepository, never()).countUnreadPerConversationForReader(anyCollection(), any());
        verify(userRepository, never()).findAllById(anyCollection());
    }

    // ── 1b. Past-end page preserves totalElements (manual-test catch) ──────

    @Test
    void pageBeyondLast_preservesTotalElements() {
        // User has 4 conversations; client asks for page 2 of size 2.
        // The repo's page object has empty content but totalElements = 4. The
        // service must NOT short-circuit to `Page.empty(pageable)` because
        // that would drop the count to 0 and hide from the client that they
        // overshot. The empty-collection guard still has to fire (avoid
        // `IN ()` on the native query), but with the original total preserved.
        Pageable pageable = PageRequest.of(2, 2);
        when(conversationRepository.findMentorPairConversationsForUserOrderedByLastMessage(
                eq(REQUESTER_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 4L));

        Page<MentorPairInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(4L);
        // Helper queries still skipped — empty IN list would throw on the native query.
        verify(messageRepository, never()).findLatestPerConversation(anyCollection());
        verify(messageRepository, never()).countUnreadPerConversationForReader(anyCollection(), any());
        verify(userRepository, never()).findAllById(anyCollection());
    }

    // ── 2. Single-conversation happy path: every field is populated ────────

    @Test
    void singleConversation_mapsAllFieldsCorrectly() {
        Long conversationId = 901L;
        Long peerId = 200L;
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-05-04T10:00:00Z");

        Pageable pageable = PageRequest.of(0, 20);
        Conversation c = pairConversation(conversationId, REQUESTER_ID, peerId);
        Mentor peer = mentor(peerId, "Mira");

        stubPage(pageable, List.of(c));
        when(messageRepository.findLatestPerConversation(List.of(conversationId)))
                .thenReturn(List.of(latest(conversationId, "see you Tuesday", sentAt)));
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(conversationId), REQUESTER_ID))
                .thenReturn(List.of(unread(conversationId, 2L)));
        when(userRepository.findAllById(List.of(peerId))).thenReturn(List.<User>of(peer));

        Page<MentorPairInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        MentorPairInboxItem item = result.getContent().get(0);
        assertThat(item.getConversationId()).isEqualTo(conversationId);
        assertThat(item.getPeerId()).isEqualTo(peerId);
        assertThat(item.getPeerFirstName()).isEqualTo("Mira");
        assertThat(item.getLastMessageContent()).isEqualTo("see you Tuesday");
        assertThat(item.getLastMessageSentAt()).isEqualTo(sentAt);
        assertThat(item.getUnreadCount()).isEqualTo(2L);
    }

    // ── 3. Service preserves repository ORDER BY (does not re-sort) ────────

    @Test
    void multipleConversations_preservesPageOrder() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation cFirst = pairConversation(901L, REQUESTER_ID, 200L);
        Conversation cSecond = pairConversation(902L, REQUESTER_ID, 201L);
        // Repository returns [cFirst, cSecond] — service must keep this order.
        stubPage(pageable, List.of(cFirst, cSecond));
        when(messageRepository.findLatestPerConversation(List.of(901L, 902L)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L, 902L), REQUESTER_ID))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of(200L, 201L)))
                .thenReturn(List.<User>of(mentor(200L, "Mira"), mentor(201L, "Mira")));

        Page<MentorPairInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent())
                .extracting(MentorPairInboxItem::getConversationId)
                .containsExactly(901L, 902L);
    }

    // ── 4. Unread-count batch value is used verbatim ───────────────────────

    @Test
    void unreadCount_takesValueFromBatchMap() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation c = pairConversation(901L, REQUESTER_ID, 200L);
        stubPage(pageable, List.of(c));
        when(messageRepository.findLatestPerConversation(List.of(901L)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L), REQUESTER_ID))
                .thenReturn(List.of(unread(901L, 5L)));
        when(userRepository.findAllById(List.of(200L)))
                .thenReturn(List.<User>of(mentor(200L, "Mira")));

        Page<MentorPairInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUnreadCount()).isEqualTo(5L);
    }

    // ── 5. Missing unread row defaults to zero (Map.getOrDefault branch) ───

    @Test
    void unreadCount_zeroWhenAllRead() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation c = pairConversation(901L, REQUESTER_ID, 200L);
        stubPage(pageable, List.of(c));
        when(messageRepository.findLatestPerConversation(List.of(901L)))
                .thenReturn(List.of());
        // No row in the unread aggregate -> default 0.
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L), REQUESTER_ID))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of(200L)))
                .thenReturn(List.<User>of(mentor(200L, "Mira")));

        Page<MentorPairInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent().get(0).getUnreadCount()).isZero();
    }

    // ── 6. Latest-message batch maps each conversation to its own latest ───

    @Test
    void latestMessage_isPickedFromBatchedResult() {
        Pageable pageable = PageRequest.of(0, 20);
        OffsetDateTime t1 = OffsetDateTime.parse("2026-05-01T08:00:00Z");
        OffsetDateTime t2 = OffsetDateTime.parse("2026-05-04T08:00:00Z");
        Conversation c1 = pairConversation(901L, REQUESTER_ID, 200L);
        Conversation c2 = pairConversation(902L, REQUESTER_ID, 201L);
        stubPage(pageable, List.of(c1, c2));
        when(messageRepository.findLatestPerConversation(List.of(901L, 902L)))
                .thenReturn(List.of(
                        latest(901L, "old", t1),
                        latest(902L, "fresh", t2)));
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L, 902L), REQUESTER_ID))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of(200L, 201L)))
                .thenReturn(List.<User>of(mentor(200L, "Mira"), mentor(201L, "Mira")));

        List<MentorPairInboxItem> items = inboxService.listInbox(REQUESTER_ID, pageable).getContent();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getLastMessageContent()).isEqualTo("old");
        assertThat(items.get(0).getLastMessageSentAt()).isEqualTo(t1);
        assertThat(items.get(1).getLastMessageContent()).isEqualTo("fresh");
        assertThat(items.get(1).getLastMessageSentAt()).isEqualTo(t2);
    }

    // ── 7. Conversation with no messages renders nulls + zero unread ───────

    @Test
    void conversationWithNoMessages_rendersNullsAndZeroUnread() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation c = pairConversation(901L, REQUESTER_ID, 200L);
        stubPage(pageable, List.of(c));
        // Both batch lookups return empty for this conversation.
        when(messageRepository.findLatestPerConversation(List.of(901L))).thenReturn(List.of());
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L), REQUESTER_ID)).thenReturn(List.of());
        when(userRepository.findAllById(List.of(200L)))
                .thenReturn(List.<User>of(mentor(200L, "Mira")));

        MentorPairInboxItem item = inboxService.listInbox(REQUESTER_ID, pageable).getContent().get(0);

        assertThat(item.getLastMessageContent()).isNull();
        assertThat(item.getLastMessageSentAt()).isNull();
        assertThat(item.getUnreadCount()).isZero();
    }

    // ── 8. Race-defensive null-peer filter; totalElements preserved ────────

    @Test
    void racedPeerDeletion_dropsConversationFromResult() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation c1 = pairConversation(901L, REQUESTER_ID, 200L); // peer 200 still exists
        Conversation c2 = pairConversation(902L, REQUESTER_ID, 201L); // peer 201 was deleted
        // Repository reports two conversations, totalElements = 2.
        when(conversationRepository.findMentorPairConversationsForUserOrderedByLastMessage(
                eq(REQUESTER_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pageable, 2L));
        when(messageRepository.findLatestPerConversation(List.of(901L, 902L)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L, 902L), REQUESTER_ID))
                .thenReturn(List.of());
        // findAllById returns only the peer that still exists — simulating the
        // narrow window where peer 201's account commit-deleted between the
        // page query and the peer load.
        when(userRepository.findAllById(List.of(200L, 201L)))
                .thenReturn(List.<User>of(mentor(200L, "Mira")));

        Page<MentorPairInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent())
                .extracting(MentorPairInboxItem::getConversationId)
                .containsExactly(901L);
        // PageImpl's invariant: when (offset + pageSize > total), the total
        // is auto-corrected to (offset + content.size()) so the page stays
        // self-consistent. Here offset=0, pageSize=20, supplied total=2, so
        // the post-filter content.size()=1 drives the visible total to 1.
        // This matches what the next request will return after the
        // ON DELETE CASCADE propagates — exactly the right behaviour for the
        // race window.
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void stubPage(Pageable pageable, List<Conversation> content) {
        when(conversationRepository.findMentorPairConversationsForUserOrderedByLastMessage(
                eq(REQUESTER_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(content, pageable, content.size()));
    }

    private static Conversation pairConversation(Long id, Long requesterId, Long peerId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setKind(ConversationKind.MENTOR_PAIR);
        // The DB CHECK constraint conversations_pair_ordering enforces
        // pair_a_id < pair_b_id; honour it here so the test fixtures stay
        // aligned with production data.
        c.setPairAId(Math.min(requesterId, peerId));
        c.setPairBId(Math.max(requesterId, peerId));
        return c;
    }

    private static Mentor mentor(Long id, String firstName) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setFirstName(firstName);
        return m;
    }

    private static ConversationLastMessageView latest(Long conversationId, String content, OffsetDateTime sentAt) {
        Instant asInstant = sentAt.toInstant();
        return new ConversationLastMessageView() {
            @Override public Long getConversationId() { return conversationId; }
            @Override public String getContent() { return content; }
            @Override public Instant getSentAt() { return asInstant; }
        };
    }

    private static ConversationUnreadCount unread(Long conversationId, Long count) {
        return new ConversationUnreadCount() {
            @Override public Long getConversationId() { return conversationId; }
            @Override public Long getUnreadCount() { return count; }
        };
    }
}
