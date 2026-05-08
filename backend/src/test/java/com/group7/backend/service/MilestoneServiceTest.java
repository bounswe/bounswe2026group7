package com.group7.backend.service;

import com.group7.backend.dto.request.MilestoneActionItemCreateRequest;
import com.group7.backend.dto.request.MilestoneActionItemUpdateRequest;
import com.group7.backend.dto.request.MilestoneCreateRequest;
import com.group7.backend.dto.request.MilestoneUpdateRequest;
import com.group7.backend.entity.*;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MilestoneActionItemRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock private MilestoneRepository milestoneRepository;
    @Mock private MilestoneActionItemRepository actionItemRepository;
    @Mock private MentorshipRepository mentorshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationEventPublisher notificationEventPublisher;

    @InjectMocks
    private MilestoneService milestoneService;

    private Mentorship activeMentorship;
    private Milestone pendingMilestone;
    private MilestoneActionItem actionItem;
    private Mentor mentor;
    private Mentee mentee;

    @BeforeEach
    void setUp() {
        mentor = new Mentor(); mentor.setId(1L); mentor.setFirstName("Mentor");
        mentee = new Mentee(); mentee.setId(2L); mentee.setFirstName("Mentee");

        activeMentorship = new Mentorship();
        activeMentorship.setId(10L);
        activeMentorship.setMentor(mentor);
        activeMentorship.setMentee(mentee);
        activeMentorship.setStatus(MentorshipStatus.ACTIVE);

        pendingMilestone = new Milestone();
        pendingMilestone.setId(100L);
        pendingMilestone.setMentorship(activeMentorship);
        pendingMilestone.setTitle("Pending Milestone");
        pendingMilestone.setStatus(MilestoneStatus.PENDING);

        actionItem = new MilestoneActionItem();
        actionItem.setId(500L);
        actionItem.setMilestone(pendingMilestone);
        actionItem.setText("Initial text");
        actionItem.setCompleted(false);
        actionItem.setCreatedBy(mentor);
    }

    @Test
    void createMilestone_Success() {
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        when(milestoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MilestoneCreateRequest req = new MilestoneCreateRequest();
        req.setTitle("New Milestone");

        var response = milestoneService.createMilestone(10L, 1L, req);

        assertThat(response.getTitle()).isEqualTo("New Milestone");
        assertThat(response.getStatus()).isEqualTo(MilestoneStatus.PENDING);
        verify(notificationEventPublisher).publishMilestoneCreated(2L, "New Milestone");
    }

    @Test
    void createMilestone_MenteeForbidden() {
        when(mentorshipRepository.findById(10L)).thenReturn(Optional.of(activeMentorship));
        MilestoneCreateRequest req = new MilestoneCreateRequest();

        assertThatThrownBy(() -> milestoneService.createMilestone(10L, 2L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only mentors can create");
    }

    @Test
    void updateActionItem_Text_MentorSuccess() {
        when(actionItemRepository.findByIdWithMilestoneAndMentorship(500L)).thenReturn(Optional.of(actionItem));
        when(actionItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MilestoneActionItemUpdateRequest req = new MilestoneActionItemUpdateRequest();
        req.setText("Updated text");

        var response = milestoneService.updateActionItem(500L, 1L, req);

        assertThat(response.getText()).isEqualTo("Updated text");
    }

    @Test
    void updateActionItem_Text_MenteeForbidden() {
        when(actionItemRepository.findByIdWithMilestoneAndMentorship(500L)).thenReturn(Optional.of(actionItem));

        MilestoneActionItemUpdateRequest req = new MilestoneActionItemUpdateRequest();
        req.setText("Mentee trying to update text");

        assertThatThrownBy(() -> milestoneService.updateActionItem(500L, 2L, req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only mentors can edit action item text");
    }

    @Test
    void updateActionItem_Toggle_MenteeSuccess() {
        when(actionItemRepository.findByIdWithMilestoneAndMentorship(500L)).thenReturn(Optional.of(actionItem));
        when(userRepository.getReferenceById(2L)).thenReturn(mentee);
        when(actionItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MilestoneActionItemUpdateRequest req = new MilestoneActionItemUpdateRequest();
        req.setIsCompleted(true); // Mentee should be able to toggle this!

        var response = milestoneService.updateActionItem(500L, 2L, req);

        assertThat(response.isCompleted()).isTrue();
    }
}
