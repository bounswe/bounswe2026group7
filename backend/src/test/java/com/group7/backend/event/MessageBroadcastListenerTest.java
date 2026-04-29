package com.group7.backend.event;

import com.group7.backend.dto.response.MessageResponse;
import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.Message;
import com.group7.backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageBroadcastListenerTest {

    @Mock private MessageRepository messageRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private MessageBroadcastListener listener;

    private Message message;

    @BeforeEach
    void setUp() {
        Mentor mentor = new Mentor();
        mentor.setId(1L);
        mentor.setFirstName("Mira");
        mentor.setLastName("Mentor");

        Mentee mentee = new Mentee();
        mentee.setId(2L);

        Mentorship mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);

        Conversation conversation = new Conversation();
        conversation.setId(900L);
        conversation.setKind(ConversationKind.MENTORSHIP);
        conversation.setMentorship(mentorship);

        message = new Message();
        message.setId(500L);
        message.setConversation(conversation);
        message.setSender(mentor);
        message.setContent("Hello");
        message.setSentAt(OffsetDateTime.now());
    }

    @Test
    void broadcastsMessageToTopicForConversation() {
        when(messageRepository.findByIdForBroadcast(500L)).thenReturn(Optional.of(message));

        listener.onMessageSent(new MessageSentEvent(500L, 900L, 1L, 2L));

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/conversation/900");
        assertThat(payloadCaptor.getValue()).isInstanceOf(MessageResponse.class);
        MessageResponse payload = (MessageResponse) payloadCaptor.getValue();
        assertThat(payload.getId()).isEqualTo(500L);
        assertThat(payload.getConversationId()).isEqualTo(900L);
        assertThat(payload.getContent()).isEqualTo("Hello");
    }

    @Test
    void doesNotBroadcastWhenMessageVanished() {
        when(messageRepository.findByIdForBroadcast(500L)).thenReturn(Optional.empty());

        listener.onMessageSent(new MessageSentEvent(500L, 900L, 1L, 2L));

        verifyNoInteractions(messagingTemplate);
    }
}
