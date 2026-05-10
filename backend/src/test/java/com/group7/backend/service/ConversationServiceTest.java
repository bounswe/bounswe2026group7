package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.entity.MentorshipStatus;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private com.group7.backend.repository.ConversationParticipantRepository participantRepository;
    @Mock private ConversationCreator conversationCreator;

    private ConversationService conversationService;

    private Mentor mentor;
    private Mentee mentee;
    private Mentorship mentorship;
    private Mentor mentorPeer;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository, mentorshipRepository, userRepository,
                participantRepository, conversationCreator);

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

    // ── findOrCreateForMentorship (issue #245) ──────────────────────────────

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
        verify(conversationCreator, never()).createForMentorshipInNewTx(any());
    }

    @Test
    void findOrCreate_delegatesToCreator_whenMissing() {
        Conversation created = new Conversation();
        created.setId(900L);
        created.setKind(ConversationKind.MENTORSHIP);
        created.setMentorship(mentorship);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());
        when(conversationCreator.createForMentorshipInNewTx(mentorship)).thenReturn(created);

        Conversation result = conversationService.findOrCreateForMentorship(100L, 1L);

        assertThat(result).isSameAs(created);
        verify(conversationCreator, times(1)).createForMentorshipInNewTx(mentorship);
    }

    @Test
    void findOrCreate_authorizesMentee_evenIfRequestIsForMentor() {
        Conversation existing = new Conversation();
        existing.setId(900L);
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.findOrCreateForMentorship(100L, 2L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void findOrCreate_throwsForbidden_forNonParticipant() {
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorship(100L, 999L))
                .isInstanceOf(ProfileNotVisibleException.class);

        verify(conversationRepository, never()).findByMentorshipId(any());
        verify(conversationCreator, never()).createForMentorshipInNewTx(any());
    }

    @Test
    void findOrCreate_throwsNotFound_whenMentorshipMissing() {
        when(mentorshipRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorship(404L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findOrCreate_returnsExisting_evenWhenMentorshipNotActive() {
        mentorship.setStatus(MentorshipStatus.COMPLETED);
        Conversation existing = new Conversation();
        existing.setId(900L);
        existing.setKind(ConversationKind.MENTORSHIP);
        existing.setMentorship(mentorship);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.findOrCreateForMentorship(100L, 1L);

        assertThat(result).isSameAs(existing);
        verify(conversationCreator, never()).createForMentorshipInNewTx(any());
    }

    @Test
    void findOrCreate_throwsNotFound_whenMentorshipNotActiveAndNoConversation() {
        mentorship.setStatus(MentorshipStatus.TERMINATED);
        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorship(100L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(conversationCreator, never()).createForMentorshipInNewTx(any());
    }

    @Test
    void findOrCreate_recoversFromConcurrentCreate_viaUniqueConstraintRetry() {
        Conversation winnerSnapshot = new Conversation();
        winnerSnapshot.setId(900L);
        winnerSnapshot.setKind(ConversationKind.MENTORSHIP);
        winnerSnapshot.setMentorship(mentorship);

        when(mentorshipRepository.findById(100L)).thenReturn(Optional.of(mentorship));
        when(conversationRepository.findByMentorshipId(100L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerSnapshot));
        when(conversationCreator.createForMentorshipInNewTx(mentorship))
                .thenThrow(new DataIntegrityViolationException("uq_conversations_mentorship"));

        Conversation result = conversationService.findOrCreateForMentorship(100L, 1L);

        assertThat(result).isSameAs(winnerSnapshot);
    }

    // ── findOrCreateForMentorPair (issue #284) ──────────────────────────────

    @Test
    void mentorPair_delegatesToCreator_withCanonicalOrder() {
        Conversation created = new Conversation();
        created.setId(901L);
        created.setKind(ConversationKind.MENTOR_PAIR);
        created.setPairAId(1L);
        created.setPairBId(3L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR)).thenReturn(Optional.empty());
        when(conversationCreator.createForMentorPairInNewTx(
                eq(mentor), eq(mentorPeer), eq(1L), eq(3L))).thenReturn(created);

        Conversation result = conversationService.findOrCreateForMentorPair(1L, 3L);

        assertThat(result).isSameAs(created);
        verify(conversationCreator, times(1)).createForMentorPairInNewTx(
                mentor, mentorPeer, 1L, 3L);
    }

    @Test
    void mentorPair_returnsExistingConversation_whenAlreadyPresent() {
        // Idempotent path: the conversation lookup short-circuits before any
        // userRepository call. The role check is intentionally NOT re-run on
        // existing conversations so peer history survives role changes.
        Conversation existing = new Conversation();
        existing.setId(901L);
        existing.setKind(ConversationKind.MENTOR_PAIR);
        existing.setPairAId(1L);
        existing.setPairBId(3L);

        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR)).thenReturn(Optional.of(existing));

        Conversation result = conversationService.findOrCreateForMentorPair(1L, 3L);

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).findById(any());
        verify(conversationCreator, never()).createForMentorPairInNewTx(any(), any(), anyLong(), anyLong());
    }

    @Test
    void mentorPair_canonicalisesArgumentOrder_onCreatePath() {
        // Calling (3, 1) — reverse of canonical — must still send (1, 3) to
        // the creator. This is the test that the previous version of this
        // suite missed (it only verified the read-side canonicalisation).
        Conversation created = new Conversation();
        created.setId(901L);
        created.setKind(ConversationKind.MENTOR_PAIR);
        created.setPairAId(1L);
        created.setPairBId(3L);

        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR)).thenReturn(Optional.empty());
        when(conversationCreator.createForMentorPairInNewTx(
                any(), any(), eq(1L), eq(3L))).thenReturn(created);

        Conversation result = conversationService.findOrCreateForMentorPair(3L, 1L);

        assertThat(result).isSameAs(created);
        // Whichever User went where in the create call doesn't matter — only
        // the canonical (lower, higher) pair identifiers do.
        verify(conversationCreator, times(1)).createForMentorPairInNewTx(
                any(), any(), eq(1L), eq(3L));
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
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerSnapshot));
        when(conversationCreator.createForMentorPairInNewTx(
                any(), any(), eq(1L), eq(3L)))
                .thenThrow(new DataIntegrityViolationException("uq_conversations_pair_kind"));

        Conversation result = conversationService.findOrCreateForMentorPair(1L, 3L);

        assertThat(result).isSameAs(winnerSnapshot);
    }

    @Test
    void mentorPair_recoveryWithoutRow_throwsIllegalState() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(3L)).thenReturn(Optional.of(mentorPeer));
        when(conversationRepository.findByPairAIdAndPairBIdAndKind(
                1L, 3L, ConversationKind.MENTOR_PAIR)).thenReturn(Optional.empty());
        when(conversationCreator.createForMentorPairInNewTx(
                any(), any(), eq(1L), eq(3L)))
                .thenThrow(new DataIntegrityViolationException("uq_conversations_pair_kind"));

        assertThatThrownBy(() -> conversationService.findOrCreateForMentorPair(1L, 3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transaction isolation");
    }
}
