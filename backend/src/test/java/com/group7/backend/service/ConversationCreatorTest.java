package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.ConversationKind;
import com.group7.backend.entity.ConversationParticipant;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.Mentorship;
import com.group7.backend.repository.ConversationParticipantRepository;
import com.group7.backend.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level contract for the atomic create methods. Integration tests cover
 * the same invariants end-to-end against a real Postgres; these tests pin the
 * conversation/participant shape at the unit boundary so future refactors of
 * the per-kind creation logic surface in a focused place.
 */
@ExtendWith(MockitoExtension.class)
class ConversationCreatorTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;

    private ConversationCreator creator;

    private Mentor mentor;
    private Mentee mentee;
    private Mentor mentorPeer;
    private Mentorship mentorship;

    @BeforeEach
    void setUp() {
        creator = new ConversationCreator(conversationRepository, participantRepository);

        mentor = new Mentor();
        mentor.setId(1L);
        mentee = new Mentee();
        mentee.setId(2L);
        mentorPeer = new Mentor();
        mentorPeer.setId(3L);

        mentorship = new Mentorship();
        mentorship.setId(100L);
        mentorship.setMentor(mentor);
        mentorship.setMentee(mentee);

        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            // Simulate generated id so participant rows can reference it.
            if (c.getId() == null) {
                c.setId(c.getKind() == ConversationKind.MENTORSHIP ? 900L : 901L);
            }
            return c;
        });
    }

    @Test
    void createForMentorshipInNewTx_savesConversationWithMentorshipAndTwoParticipants() {
        Conversation result = creator.createForMentorshipInNewTx(mentorship);

        assertThat(result.getKind()).isEqualTo(ConversationKind.MENTORSHIP);
        assertThat(result.getMentorship()).isSameAs(mentorship);
        assertThat(result.getPairAId()).isNull();
        assertThat(result.getPairBId()).isNull();

        ArgumentCaptor<ConversationParticipant> captor =
                ArgumentCaptor.forClass(ConversationParticipant.class);
        verify(participantRepository, times(2)).save(captor.capture());
        List<Long> participantUserIds = captor.getAllValues().stream()
                .map(p -> p.getUser().getId()).toList();
        assertThat(participantUserIds).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void createForMentorPairInNewTx_savesConversationWithCanonicalPairAndTwoParticipants() {
        Conversation result = creator.createForMentorPairInNewTx(mentor, mentorPeer, 1L, 3L);

        assertThat(result.getKind()).isEqualTo(ConversationKind.MENTOR_PAIR);
        assertThat(result.getMentorship()).isNull();
        assertThat(result.getPairAId()).isEqualTo(1L);
        assertThat(result.getPairBId()).isEqualTo(3L);

        ArgumentCaptor<ConversationParticipant> captor =
                ArgumentCaptor.forClass(ConversationParticipant.class);
        verify(participantRepository, times(2)).save(captor.capture());
        List<Long> participantUserIds = captor.getAllValues().stream()
                .map(p -> p.getUser().getId()).toList();
        assertThat(participantUserIds).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void createForMentorPairInNewTx_storesPairFieldsExactlyAsPassed() {
        // The caller (ConversationService) is responsible for canonicalising
        // (lower, higher); the creator stores them verbatim. If ConversationService
        // passed reversed args, the DB CHECK constraint would catch it — but at
        // the unit boundary we just verify the creator doesn't re-canonicalise.
        Conversation result = creator.createForMentorPairInNewTx(mentor, mentorPeer, 7L, 42L);

        assertThat(result.getPairAId()).isEqualTo(7L);
        assertThat(result.getPairBId()).isEqualTo(42L);
    }
}
