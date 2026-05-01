package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.ConversationParticipant;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MentorshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private ApplicationContext applicationContext;

    private ConversationService conversationService;

    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository, participantRepository, mentorshipRepository, applicationContext);
        // Self-injection: in unit tests we route the bean lookup back to the
        // service under test so the inner createForMentorshipInNewTx call
        // executes the real method (no Spring proxy in pure JUnit).
        org.mockito.Mockito.lenient()
                .when(applicationContext.getBean(ConversationService.class))
                .thenReturn(conversationService);

        mentor = new Mentor();
        mentor.setId(1L);
        mentor.setFirstName("Mira");

        mentee = new Mentee();
        mentee.setId(2L);
        mentee.setFirstName("Eli");

        mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);
        mentorship.setStatus(MentorshipStatus.ACTIVE);
    }

    @Test
    void findOrCreate_returnsExistingConversation_whenAlreadyPresent() {
        Conversation existing = new Conversation();
        existing.setId(900L);
        existing.setKind(ConversationKind.MENTORSHIP);
        existing.setMentorship(mentorship);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.findOrCreateForMentorship(100L, 1L);

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void findOrCreate_createsConversationAndTwoParticipants_whenMissing() {
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(900L);
            return c;
        });

        Conversation result = conversationService.findOrCreateForMentorship(100L, 1L);

        assertThat(result.getId()).isEqualTo(900L);
        assertThat(result.getKind()).isEqualTo(ConversationKind.MENTORSHIP);
        assertThat(result.getMentorship()).isSameAs(mentorship);
        verify(participantRepository, times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    void findOrCreate_authorizesMentee_evenIfRequestIsForMentor() {
        Conversation existing = new Conversation();
        existing.setId(900L);
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.of(existing));

        // Mentee (id=2) is also a participant — should be authorized.
        Conversation result = conversationService.findOrCreateForMentorship(100L, 2L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void findOrCreate_throwsForbidden_forNonParticipant() {
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorship(100L, 999L))
                .isInstanceOf(ProfileNotVisibleException.class);

        verify(conversationRepository, never()).findByMentorshipId(any());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void findOrCreate_throwsNotFound_whenMentorshipMissing() {
        when(mentorshipRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorship(404L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findOrCreate_returnsExisting_evenWhenMentorshipNotActive() {
        // History remains readable after the mentorship reaches a terminal
        // state — once the conversation row exists, status is irrelevant
        // for resolving it on subsequent reads.
        mentorship.setStatus(MentorshipStatus.COMPLETED);
        Conversation existing = new Conversation();
        existing.setId(900L);
        existing.setKind(ConversationKind.MENTORSHIP);
        existing.setMentorship(mentorship);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.findOrCreateForMentorship(100L, 1L);

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void findOrCreate_throwsNotFound_whenMentorshipNotActiveAndNoConversation() {
        // The read paths (GET /messages, PATCH /read) must not silently insert
        // empty conversation rows for rejected/completed mentorships nor leak
        // participation by returning 200.
        mentorship.setStatus(MentorshipStatus.TERMINATED);
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorship(100L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(conversationRepository, never()).save(any());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void findOrCreate_recoversFromConcurrentCreate_viaUniqueConstraintRetry() {
        Conversation winnerSnapshot = new Conversation();
        winnerSnapshot.setId(900L);
        winnerSnapshot.setKind(ConversationKind.MENTORSHIP);
        winnerSnapshot.setMentorship(mentorship);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L))
                // First call: no row yet → triggers create
                // Second call (after DataIntegrityViolation): the winner is visible
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerSnapshot));
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("uq_conversations_mentorship"));

        Conversation result = conversationService.findOrCreateForMentorship(100L, 1L);

        assertThat(result).isSameAs(winnerSnapshot);
    }
}
