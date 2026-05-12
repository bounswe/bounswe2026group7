package com.group7.backend.service;

import com.group7.backend.dto.response.AdminDirectInboxItem;
import com.group7.backend.entity.Admin;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.Mentee;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDirectInboxServiceTest {

    private static final Long REQUESTER_ID = 100L;

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;

    private AdminDirectInboxService inboxService;

    @BeforeEach
    void setUp() {
        inboxService = new AdminDirectInboxService(
                conversationRepository, messageRepository, userRepository);
    }

    @Test
    void emptyInbox_returnsEmptyPageWithoutBatchLoads() {
        Pageable pageable = PageRequest.of(0, 20);
        when(conversationRepository.findAdminDirectConversationsForUserOrderedByLastMessage(
                eq(REQUESTER_ID), eq(pageable)))
                .thenReturn(Page.empty(pageable));

        Page<AdminDirectInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(messageRepository, never()).findLatestPerConversation(anyCollection());
    }

    @Test
    void singleConversation_peerIsAdmin_setsPeerIsAdminTrue() {
        Long conversationId = 901L;
        Long adminPeerId = 99L;
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-05-12T10:00:00Z");

        Pageable pageable = PageRequest.of(0, 20);
        Conversation c = adminDirect(conversationId, REQUESTER_ID, adminPeerId);
        Admin peer = admin(adminPeerId, "Ops");

        stubPage(pageable, List.of(c));
        when(messageRepository.findLatestPerConversation(List.of(conversationId)))
                .thenReturn(List.of(latest(conversationId, "Please review the guidelines.", sentAt)));
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(conversationId), REQUESTER_ID))
                .thenReturn(List.of(unread(conversationId, 1L)));
        when(userRepository.findAllById(List.of(adminPeerId))).thenReturn(List.<User>of(peer));

        AdminDirectInboxItem item = inboxService.listInbox(REQUESTER_ID, pageable).getContent().get(0);

        assertThat(item.getConversationId()).isEqualTo(conversationId);
        assertThat(item.getPeerId()).isEqualTo(adminPeerId);
        assertThat(item.getPeerFirstName()).isEqualTo("Ops");
        assertThat(item.isPeerIsAdmin()).isTrue();
        assertThat(item.getLastMessageContent()).isEqualTo("Please review the guidelines.");
        assertThat(item.getLastMessageSentAt()).isEqualTo(sentAt);
        assertThat(item.getUnreadCount()).isEqualTo(1L);
    }

    @Test
    void adminViewer_seesMenteePeer_peerIsAdminFalse() {
        Long adminId = 99L;
        Long menteePeerId = 5L;
        Pageable pageable = PageRequest.of(0, 20);
        Conversation c = adminDirect(901L, adminId, menteePeerId);

        when(conversationRepository.findAdminDirectConversationsForUserOrderedByLastMessage(
                eq(adminId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(c), pageable, 1L));
        when(messageRepository.findLatestPerConversation(List.of(901L)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L), adminId))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of(menteePeerId)))
                .thenReturn(List.<User>of(mentee(menteePeerId, "Mira")));

        AdminDirectInboxItem item = inboxService.listInbox(adminId, pageable).getContent().get(0);

        assertThat(item.getPeerId()).isEqualTo(menteePeerId);
        assertThat(item.isPeerIsAdmin()).isFalse();
        assertThat(item.getPeerFirstName()).isEqualTo("Mira");
    }

    @Test
    void unreadCount_zeroWhenAllRead() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation c = adminDirect(901L, REQUESTER_ID, 99L);
        stubPage(pageable, List.of(c));
        when(messageRepository.findLatestPerConversation(List.of(901L)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L), REQUESTER_ID))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of(99L)))
                .thenReturn(List.<User>of(admin(99L, "Ops")));

        AdminDirectInboxItem item = inboxService.listInbox(REQUESTER_ID, pageable).getContent().get(0);

        assertThat(item.getUnreadCount()).isZero();
    }

    @Test
    void racedPeerDeletion_dropsConversationFromResult() {
        Pageable pageable = PageRequest.of(0, 20);
        Conversation c1 = adminDirect(901L, REQUESTER_ID, 99L);
        Conversation c2 = adminDirect(902L, REQUESTER_ID, 98L);
        when(conversationRepository.findAdminDirectConversationsForUserOrderedByLastMessage(
                eq(REQUESTER_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(c1, c2), pageable, 2L));
        when(messageRepository.findLatestPerConversation(List.of(901L, 902L)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversationForReader(
                List.of(901L, 902L), REQUESTER_ID))
                .thenReturn(List.of());
        when(userRepository.findAllById(List.of(99L, 98L)))
                .thenReturn(List.<User>of(admin(99L, "Ops")));

        Page<AdminDirectInboxItem> result = inboxService.listInbox(REQUESTER_ID, pageable);

        assertThat(result.getContent())
                .extracting(AdminDirectInboxItem::getConversationId)
                .containsExactly(901L);
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }

    private void stubPage(Pageable pageable, List<Conversation> content) {
        when(conversationRepository.findAdminDirectConversationsForUserOrderedByLastMessage(
                eq(REQUESTER_ID), eq(pageable)))
                .thenReturn(new PageImpl<>(content, pageable, content.size()));
    }

    private static Conversation adminDirect(Long id, Long requesterId, Long peerId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setKind(ConversationKind.ADMIN_DIRECT);
        c.setPairAId(Math.min(requesterId, peerId));
        c.setPairBId(Math.max(requesterId, peerId));
        return c;
    }

    private static Admin admin(Long id, String firstName) {
        Admin a = new Admin();
        a.setId(id);
        a.setFirstName(firstName);
        return a;
    }

    private static Mentee mentee(Long id, String firstName) {
        Mentee m = new Mentee();
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
