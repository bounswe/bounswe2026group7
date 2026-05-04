package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.ConversationParticipant;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.UserRepository;
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
    @Mock private UserRepository userRepository;
    @Mock private ApplicationContext applicationContext;

    private ConversationService conversationService;

    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;
    private Mentor mentorPeer;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository, participantRepository, mentorshipRepository,
                userRepository, applicationContext);
        // Self-injection: in unit tests we route the bean lookup back to the
        // service under test so the inner createForMentorshipInNewTx /
        // createForMentorPairInNewTx calls execute the real method (no Spring
        // proxy in pure JUnit).
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

        mentorPeer = new Mentor();
        mentorPeer.setId(3L);
        mentorPeer.setFirstName("Iris");
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

    // ── findOrCreateForMentorPair (issue #284) ──────────────────────────────

    @Test
    void mentorPair_createsConversation_withCanonicalOrderAndTwoParticipants() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(901L);
            return c;
        });

        Conversation result = conversationService.findOrCreateForMentorPair(1L, 3L);

        assertThat(result.getId()).isEqualTo(901L);
        assertThat(result.getKind()).isEqualTo(ConversationKind.MENTOR_PAIR);
        assertThat(result.getPairAId()).isEqualTo(1L);
        assertThat(result.getPairBId()).isEqualTo(3L);
        assertThat(result.getMentorship()).isNull();
        verify(participantRepository, times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    void mentorPair_returnsExistingConversation_whenAlreadyPresent() {
        Conversation existing = new Conversation();
        existing.setId(901L);
        existing.setKind(ConversationKind.MENTOR_PAIR);
        existing.setPairAId(1L);
        existing.setPairBId(3L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR))
                .thenReturn(Optional.of(existing));

        Conversation result = conversationService.findOrCreateForMentorPair(1L, 3L);

        assertThat(result).isSameAs(existing);
        verify(conversationRepository, never()).save(any());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void mentorPair_resolvesToSameConversation_regardlessOfArgumentOrder() {
        // Calling (3, 1) must produce the same canonical key (1, 3) as (1, 3).
        Conversation existing = new Conversation();
        existing.setId(901L);
        existing.setKind(ConversationKind.MENTOR_PAIR);
        existing.setPairAId(1L);
        existing.setPairBId(3L);

        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR))
                .thenReturn(Optional.of(existing));

        Conversation result = conversationService.findOrCreateForMentorPair(3L, 1L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void mentorPair_throws400_whenSelfPair() {
        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("yourself");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void mentorPair_throws404_whenRequesterNotFound() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(404L, 3L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mentorPair_throws404_whenOtherNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(1L, 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mentorPair_throws400_whenRequesterIsNotAMentor() {
        // Defense-in-depth: controller @PreAuthorize blocks mentees, but the
        // service rejects a mentee caller too if invoked from non-controller code.
        when(userRepository.findById(2L)).thenReturn(Optional.of(mentee));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(2L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mentors");
    }

    @Test
    void mentorPair_throws400_whenOtherIsNotAMentor() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(2L)).thenReturn(Optional.of(mentee));

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mentors");
    }

    @Test
    void mentorPair_throws400_whenOtherIsAnAdmin() {
        // Admin extends User but is not a Mentor — instanceof check rejects.
        com.group7.backend.entity.Admin admin = new com.group7.backend.entity.Admin();
        admin.setId(99L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(99L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mentors");
    }

    @Test
    void mentorPair_recoversFromConcurrentCreate_viaUniqueConstraintRetry() {
        Conversation winnerSnapshot = new Conversation();
        winnerSnapshot.setId(901L);
        winnerSnapshot.setKind(ConversationKind.MENTOR_PAIR);
        winnerSnapshot.setPairAId(1L);
        winnerSnapshot.setPairBId(3L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR))
                // First call: no row yet → triggers create
                // Second call (after DataIntegrityViolation): the winner is visible
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerSnapshot));
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("uq_conversations_pair_kind"));

        Conversation result = conversationService.findOrCreateForMentorPair(1L, 3L);

        assertThat(result).isSameAs(winnerSnapshot);
    }

    @Test
    void mentorPair_recoveryWithoutRow_throwsIllegalState() {
        // Inner tx throws but the recovery re-find sees nothing — this should
        // never happen in production (the unique index guarantees the winning
        // row is committed by the time the loser re-reads). Surface as a
        // 500-class IllegalStateException with diagnostic context so the
        // operator knows transaction isolation is misconfigured.
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR))
                .thenReturn(Optional.empty());
        when(conversationRepository.save(any(Conversation.class)))
                .thenThrow(new DataIntegrityViolationException("uq_conversations_pair_kind"));

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(1L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction isolation");
    }
}
