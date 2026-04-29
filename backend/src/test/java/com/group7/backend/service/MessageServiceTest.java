package com.group7.backend.service;

import com.group7.backend.dto.request.SendMessageRequest;
import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.ConversationParticipant;
import com.group7.backend.entity.ConversationParticipantId;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.Message;
import com.group7.backend.event.MessageSentEvent;
import com.group7.backend.exception.MentorshipRequestException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MessageRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    @Spy private Clock clock = Clock.systemUTC();

    @InjectMocks private MessageService messageService;

    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        mentor = new Mentor();
        mentor.setId(1L);
        mentor.setEmail("mentor@example.com");
        mentor.setFirstName("Mira");
        mentor.setLastName("Mentor");

        mentee = new Mentee();
        mentee.setId(2L);
        mentee.setEmail("mentee@example.com");
        mentee.setFirstName("Eli");
        mentee.setLastName("Mentee");

        mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);

        conversation = new Conversation();
        conversation.setId(900L);
        conversation.setKind(ConversationKind.MENTORSHIP);
        conversation.setMentorship(mentorship);
        conversation.setParticipants(participantsOf(conversation, mentor, mentee));
    }

    @Test
    void send_byMentor_persistsAndEmitsEventAndNotifiesMentee() {
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 1L)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(900L, 1L)).thenReturn(List.of(2L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(500L);
            return m;
        });

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Hello, mentee");

        MessageResponse response = messageService.send(1L, 900L, req);

        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getConversationId()).isEqualTo(900L);
        assertThat(response.getMentorshipId()).isEqualTo(100L);
        assertThat(response.getSenderId()).isEqualTo(1L);
        assertThat(response.getContent()).isEqualTo("Hello, mentee");
        assertThat(response.getReadAt()).isNull();

        ArgumentCaptor<MessageSentEvent> eventCaptor = ArgumentCaptor.forClass(MessageSentEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        MessageSentEvent event = eventCaptor.getValue();
        assertThat(event.messageId()).isEqualTo(500L);
        assertThat(event.conversationId()).isEqualTo(900L);
        assertThat(event.senderId()).isEqualTo(1L);
        assertThat(event.recipientId()).isEqualTo(2L);

        verify(notificationEventPublisher).publishNewMessage(eq(2L), eq("Mira"));
    }

    @Test
    void send_byMentee_routesToMentorAsRecipient() {
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 2L)).thenReturn(true);
        when(participantRepository.findOtherParticipantUserIds(900L, 2L)).thenReturn(List.of(1L));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mentee));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(501L);
            return m;
        });

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("Thanks");

        messageService.send(2L, 900L, req);

        ArgumentCaptor<MessageSentEvent> eventCaptor = ArgumentCaptor.forClass(MessageSentEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().recipientId()).isEqualTo(1L);
        verify(notificationEventPublisher).publishNewMessage(eq(1L), eq("Eli"));
    }

    @Test
    void send_byNonParticipant_throwsForbidden() {
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 999L)).thenReturn(false);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("intruder");

        assertThatThrownBy(() -> messageService.send(999L, 900L, req))
                .isInstanceOf(ProfileNotVisibleException.class);

        verify(messageRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
        verify(notificationEventPublisher, never()).publishNewMessage(any(), anyString());
    }

    @Test
    void send_onMissingConversation_throwsNotFound() {
        when(conversationRepository.findById(404L)).thenReturn(Optional.empty());

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("nope");

        assertThatThrownBy(() -> messageService.send(1L, 404L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void send_onInactiveMentorship_throwsConflict() {
        mentorship.setStatus(MentorshipStatus.COMPLETED);
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 1L)).thenReturn(true);

        SendMessageRequest req = new SendMessageRequest();
        req.setContent("after end");

        assertThatThrownBy(() -> messageService.send(1L, 900L, req))
                .isInstanceOf(MentorshipRequestException.class);

        verify(messageRepository, never()).save(any());
    }

    @Test
    void list_byParticipant_returnsMappedPage() {
        Message persisted = newPersistedMessage(700L, mentor, "history");
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 2L)).thenReturn(true);
        Page<Message> page = new PageImpl<>(List.of(persisted));
        when(messageRepository.findByConversationIdOrderBySentAtDescIdDesc(eq(900L), any(Pageable.class)))
                .thenReturn(page);

        Page<MessageResponse> result = messageService.list(2L, 900L, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(700L);
        assertThat(result.getContent().get(0).getConversationId()).isEqualTo(900L);
    }

    @Test
    void list_byNonParticipant_throwsForbidden() {
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 999L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.list(999L, 900L, PageRequest.of(0, 20)))
                .isInstanceOf(ProfileNotVisibleException.class);

        verify(messageRepository, never())
                .findByConversationIdOrderBySentAtDescIdDesc(any(), any(Pageable.class));
    }

    @Test
    void markAllRead_byMentee_updatesMentorMessages() {
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 2L)).thenReturn(true);
        when(messageRepository.markAllAsReadForReader(eq(900L), eq(2L), any(OffsetDateTime.class)))
                .thenReturn(3);

        int updated = messageService.markAllRead(2L, 900L);

        assertThat(updated).isEqualTo(3);
    }

    @Test
    void markAllRead_byNonParticipant_throwsForbidden() {
        when(conversationRepository.findById(900L)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(900L, 999L)).thenReturn(false);

        assertThatThrownBy(() -> messageService.markAllRead(999L, 900L))
                .isInstanceOf(ProfileNotVisibleException.class);

        verify(messageRepository, never())
                .markAllAsReadForReader(any(), any(), any(OffsetDateTime.class));
    }

    private Message newPersistedMessage(Long id, com.group7.backend.entity.User sender, String content) {
        Message m = new Message();
        m.setId(id);
        m.setConversation(conversation);
        m.setSender(sender);
        m.setContent(content);
        m.setSentAt(OffsetDateTime.now());
        return m;
    }

    private static Set<ConversationParticipant> participantsOf(Conversation c, com.group7.backend.entity.User... users) {
        Set<ConversationParticipant> set = new LinkedHashSet<>();
        for (com.group7.backend.entity.User u : users) {
            ConversationParticipant cp = new ConversationParticipant();
            cp.setId(new ConversationParticipantId(c.getId(), u.getId()));
            cp.setConversation(c);
            cp.setUser(u);
            set.add(cp);
        }
        return set;
    }
}
