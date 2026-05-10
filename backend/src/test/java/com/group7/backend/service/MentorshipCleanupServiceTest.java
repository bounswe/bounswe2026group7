package com.group7.backend.service;

import com.group7.backend.entity.Conversation;
import com.group7.backend.entity.Meeting;
import com.group7.backend.entity.Milestone;
import com.group7.backend.entity.Task;
import com.group7.backend.repository.ConversationRepository;
import com.group7.backend.repository.MeetingRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.SentMilestoneReminderRepository;
import com.group7.backend.repository.SentTaskReminderRepository;
import com.group7.backend.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorshipCleanupServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private SentTaskReminderRepository sentTaskReminderRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private SentMilestoneReminderRepository sentMilestoneReminderRepository;
    @Mock private MeetingRepository meetingRepository;
    @Mock private ConversationRepository conversationRepository;

    @InjectMocks private MentorshipCleanupService mentorshipCleanupService;

    private static Task task(Long id) {
        Task t = new Task();
        t.setId(id);
        return t;
    }

    private static Milestone milestone(Long id) {
        Milestone m = new Milestone();
        m.setId(id);
        return m;
    }

    private static Meeting meeting(Long id) {
        Meeting m = new Meeting();
        m.setId(id);
        return m;
    }

    @Test
    void deletesTasksAndTheirReminders() {
        when(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(100L))
                .thenReturn(List.of(task(11L), task(12L)));
        when(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(100L)).thenReturn(List.of());
        when(meetingRepository.findByMentorshipIdOrderByStartTimeAsc(100L)).thenReturn(List.of());
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());

        mentorshipCleanupService.cleanupChildren(100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(sentTaskReminderRepository).deleteByTaskIdIn(captor.capture());
        assertThat(captor.getValue()).containsExactly(11L, 12L);
        verify(taskRepository).deleteAllInBatch(any());
    }

    @Test
    void deletesMilestonesAndTheirReminders() {
        when(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
        when(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(100L))
                .thenReturn(List.of(milestone(21L), milestone(22L)));
        when(meetingRepository.findByMentorshipIdOrderByStartTimeAsc(100L)).thenReturn(List.of());
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());

        mentorshipCleanupService.cleanupChildren(100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(sentMilestoneReminderRepository).deleteByMilestoneIdIn(captor.capture());
        assertThat(captor.getValue()).containsExactly(21L, 22L);
        verify(milestoneRepository).deleteAllInBatch(any());
    }

    @Test
    void deletesMeetingsInBatch() {
        when(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
        when(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(100L)).thenReturn(List.of());
        when(meetingRepository.findByMentorshipIdOrderByStartTimeAsc(100L))
                .thenReturn(List.of(meeting(31L), meeting(32L)));
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());

        mentorshipCleanupService.cleanupChildren(100L);

        verify(meetingRepository).deleteAllInBatch(any());
    }

    @Test
    void deletesConversationWhenPresent() {
        Conversation conversation = new Conversation();
        conversation.setId(500L);

        when(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
        when(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(100L)).thenReturn(List.of());
        when(meetingRepository.findByMentorshipIdOrderByStartTimeAsc(100L)).thenReturn(List.of());
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.of(conversation));

        mentorshipCleanupService.cleanupChildren(100L);

        verify(conversationRepository).delete(conversation);
    }

    @Test
    void skipsReminderDeleteWhenNoChildren() {
        when(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(100L)).thenReturn(List.of());
        when(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(100L)).thenReturn(List.of());
        when(meetingRepository.findByMentorshipIdOrderByStartTimeAsc(100L)).thenReturn(List.of());
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.empty());

        mentorshipCleanupService.cleanupChildren(100L);

        verify(sentTaskReminderRepository, never()).deleteByTaskIdIn(any());
        verify(sentMilestoneReminderRepository, never()).deleteByMilestoneIdIn(any());
        verify(taskRepository, never()).deleteAllInBatch(any());
        verify(milestoneRepository, never()).deleteAllInBatch(any());
        verify(meetingRepository, never()).deleteAllInBatch(any());
        verify(conversationRepository, never()).delete(any(Conversation.class));
    }

    @Test
    void cleansUpAllChildTypesInOneCall() {
        when(taskRepository.findByMentorshipIdOrderByCreatedAtDesc(100L)).thenReturn(List.of(task(11L)));
        when(milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(100L)).thenReturn(List.of(milestone(21L)));
        when(meetingRepository.findByMentorshipIdOrderByStartTimeAsc(100L)).thenReturn(List.of(meeting(31L)));
        Conversation conversation = new Conversation();
        conversation.setId(500L);
        when(conversationRepository.findByMentorshipId(100L)).thenReturn(Optional.of(conversation));

        mentorshipCleanupService.cleanupChildren(100L);

        verify(sentTaskReminderRepository).deleteByTaskIdIn(eq(List.of(11L)));
        verify(taskRepository).deleteAllInBatch(any());
        verify(sentMilestoneReminderRepository).deleteByMilestoneIdIn(eq(List.of(21L)));
        verify(milestoneRepository).deleteAllInBatch(any());
        verify(meetingRepository).deleteAllInBatch(any());
        verify(conversationRepository).delete(conversation);
    }
}
