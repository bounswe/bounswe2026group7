package com.group7.backend.service;

import com.group7.backend.dto.request.MilestoneActionItemCreateRequest;
import com.group7.backend.dto.request.MilestoneActionItemUpdateRequest;
import com.group7.backend.dto.request.MilestoneCreateRequest;
import com.group7.backend.dto.request.MilestoneUpdateRequest;
import com.group7.backend.dto.response.MilestoneActionItemResponse;
import com.group7.backend.dto.response.MilestoneDetailResponse;
import com.group7.backend.dto.response.MilestoneSummaryResponse;
import com.group7.backend.entity.*;
import com.group7.backend.exception.MilestoneConflictException;
import com.group7.backend.exception.ProfileNotVisibleException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MentorshipRepository;
import com.group7.backend.repository.MilestoneActionItemRepository;
import com.group7.backend.repository.MilestoneRepository;
import com.group7.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final MilestoneActionItemRepository actionItemRepository;
    private final MentorshipRepository mentorshipRepository;
    private final UserRepository userRepository;
    private final NotificationEventPublisher notificationEventPublisher;

    public MilestoneService(MilestoneRepository milestoneRepository,
                            MilestoneActionItemRepository actionItemRepository,
                            MentorshipRepository mentorshipRepository,
                            UserRepository userRepository,
                            NotificationEventPublisher notificationEventPublisher) {
        this.milestoneRepository = milestoneRepository;
        this.actionItemRepository = actionItemRepository;
        this.mentorshipRepository = mentorshipRepository;
        this.userRepository = userRepository;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    public MilestoneDetailResponse createMilestone(Long mentorshipId, Long mentorId, MilestoneCreateRequest request) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));

        if (!mentorship.getMentor().getId().equals(mentorId)) {
            throw new ProfileNotVisibleException("Only mentors can create milestones");
        }
        checkMentorshipActive(mentorship);

        if (request.getTargetDate() != null) {
            ensureWithinMentorship(mentorship, request.getTargetDate());
        }

        Milestone milestone = new Milestone();
        milestone.setMentorship(mentorship);
        milestone.setTitle(request.getTitle());
        milestone.setDescription(request.getDescription());
        milestone.setTargetDate(request.getTargetDate());
        milestone.setOrderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 0);
        milestone.setStatus(MilestoneStatus.PENDING);

        milestone = milestoneRepository.save(milestone);

        notificationEventPublisher.publishMilestoneCreated(mentorship.getMentee().getId(), milestone.getTitle());

        return mapToDetailResponse(milestone);
    }

    @Transactional(readOnly = true)
    public List<MilestoneSummaryResponse> listMilestones(Long mentorshipId, Long userId) {
        Mentorship mentorship = mentorshipRepository.findById(mentorshipId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentorship not found"));

        validateParticipant(mentorship, userId);

        return milestoneRepository.findByMentorshipIdOrderByOrderIndexAsc(mentorshipId)
                .stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MilestoneDetailResponse getMilestone(Long id, Long userId) {
        Milestone milestone = milestoneRepository.findByIdWithMentorship(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));

        validateParticipant(milestone.getMentorship(), userId);

        return mapToDetailResponse(milestone);
    }

    public MilestoneDetailResponse updateMilestone(Long id, Long mentorId, MilestoneUpdateRequest request) {
        Milestone milestone = milestoneRepository.findByIdWithMentorship(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));

        if (!milestone.getMentorship().getMentor().getId().equals(mentorId)) {
            throw new ProfileNotVisibleException("Only mentors can update milestones");
        }
        checkMentorshipActive(milestone.getMentorship());

        if (request.getTitle() != null) milestone.setTitle(request.getTitle());
        if (request.getDescription() != null) milestone.setDescription(request.getDescription());
        if (request.getTargetDate() != null) {
            ensureWithinMentorship(milestone.getMentorship(), request.getTargetDate());
            milestone.setTargetDate(request.getTargetDate());
        }
        if (request.getOrderIndex() != null) milestone.setOrderIndex(request.getOrderIndex());
        
        if (request.getStatus() != null && milestone.getStatus() != request.getStatus()) {
            milestone.setStatus(request.getStatus());
            if (request.getStatus() == MilestoneStatus.COMPLETED) {
                milestone.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                notificationEventPublisher.publishMilestoneCompleted(
                        milestone.getMentorship().getMentee().getId(),
                        milestone.getTitle(),
                        false
                );
            } else {
                milestone.setCompletedAt(null);
            }
        }

        return mapToDetailResponse(milestoneRepository.save(milestone));
    }

    public void deleteMilestone(Long id, Long mentorId) {
        Milestone milestone = milestoneRepository.findByIdWithMentorship(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));

        if (!milestone.getMentorship().getMentor().getId().equals(mentorId)) {
            throw new ProfileNotVisibleException("Only mentors can delete milestones");
        }
        checkMentorshipActive(milestone.getMentorship());

        milestoneRepository.delete(milestone);
    }

    // --- Action Items ---

    public MilestoneActionItemResponse addActionItem(Long milestoneId, Long mentorId, MilestoneActionItemCreateRequest request) {
        Milestone milestone = milestoneRepository.findByIdWithMentorship(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found"));

        if (!milestone.getMentorship().getMentor().getId().equals(mentorId)) {
            throw new ProfileNotVisibleException("Only mentors can add action items");
        }
        checkMentorshipActive(milestone.getMentorship());

        User creator = userRepository.getReferenceById(mentorId);

        MilestoneActionItem item = new MilestoneActionItem();
        item.setMilestone(milestone);
        item.setText(request.getText());
        item.setOrderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 0);
        item.setCreatedBy(creator);

        return mapToActionItemResponse(actionItemRepository.save(item));
    }

    public MilestoneActionItemResponse updateActionItem(Long id, Long userId, MilestoneActionItemUpdateRequest request) {
        MilestoneActionItem item = actionItemRepository.findByIdWithMilestoneAndMentorship(id)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found"));

        Mentorship mentorship = item.getMilestone().getMentorship();
        validateParticipant(mentorship, userId);
        checkMentorshipActive(mentorship);

        boolean isMentor = mentorship.getMentor().getId().equals(userId);

        // Text editing is mentor-only
        if (request.getText() != null) {
            if (!isMentor) {
                throw new ProfileNotVisibleException("Only mentors can edit action item text");
            }
            item.setText(request.getText());
        }

        // Order editing is mentor-only
        if (request.getOrderIndex() != null) {
            if (!isMentor) {
                throw new ProfileNotVisibleException("Only mentors can reorder action items");
            }
            item.setOrderIndex(request.getOrderIndex());
        }

        // Toggling is shared
        if (request.getIsCompleted() != null && item.isCompleted() != request.getIsCompleted()) {
            item.setCompleted(request.getIsCompleted());
            if (request.getIsCompleted()) {
                item.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                item.setCompletedBy(userRepository.getReferenceById(userId));
                
                // If the mentee completes the item, notify the mentor
                if (!isMentor) {
                    notificationEventPublisher.publishActionItemCompleted(
                            mentorship.getMentor().getId(),
                            mentorship.getMentee().getFirstName(),
                            item.getText()
                    );
                }
            } else {
                item.setCompletedAt(null);
                item.setCompletedBy(null);
            }
        }

        return mapToActionItemResponse(actionItemRepository.save(item));
    }

    public void deleteActionItem(Long id, Long mentorId) {
        MilestoneActionItem item = actionItemRepository.findByIdWithMilestoneAndMentorship(id)
                .orElseThrow(() -> new ResourceNotFoundException("Action item not found"));

        if (!item.getMilestone().getMentorship().getMentor().getId().equals(mentorId)) {
            throw new ProfileNotVisibleException("Only mentors can delete action items");
        }
        checkMentorshipActive(item.getMilestone().getMentorship());

        actionItemRepository.delete(item);
    }

    // --- Private Helpers ---

    private void validateParticipant(Mentorship mentorship, Long userId) {
        boolean isMentor = mentorship.getMentor().getId().equals(userId);
        boolean isMentee = mentorship.getMentee().getId().equals(userId);
        if (!isMentor && !isMentee) {
            throw new ProfileNotVisibleException("You are not a participant in this mentorship");
        }
    }

    private void checkMentorshipActive(Mentorship mentorship) {
        if (mentorship.getStatus() != MentorshipStatus.ACTIVE) {
            throw new MilestoneConflictException("Mentorship is not active");
        }
    }

    private MilestoneSummaryResponse mapToSummaryResponse(Milestone milestone) {
        MilestoneSummaryResponse dto = new MilestoneSummaryResponse();
        dto.setId(milestone.getId());
        dto.setTitle(milestone.getTitle());
        dto.setTargetDate(milestone.getTargetDate());
        dto.setStatus(milestone.getStatus());
        dto.setOrderIndex(milestone.getOrderIndex());
        return dto;
    }

    private MilestoneDetailResponse mapToDetailResponse(Milestone milestone) {
        MilestoneDetailResponse dto = new MilestoneDetailResponse();
        dto.setId(milestone.getId());
        dto.setMentorshipId(milestone.getMentorship().getId());
        dto.setTitle(milestone.getTitle());
        dto.setDescription(milestone.getDescription());
        dto.setTargetDate(milestone.getTargetDate());
        dto.setStatus(milestone.getStatus());
        dto.setOrderIndex(milestone.getOrderIndex());
        dto.setCompletedAt(milestone.getCompletedAt());
        dto.setCreatedAt(milestone.getCreatedAt());

        List<MilestoneActionItemResponse> actionItems = actionItemRepository
                .findByMilestoneIdOrderByOrderIndexAsc(milestone.getId())
                .stream()
                .map(this::mapToActionItemResponse)
                .collect(Collectors.toList());
        dto.setActionItems(actionItems);

        return dto;
    }

    private MilestoneActionItemResponse mapToActionItemResponse(MilestoneActionItem item) {
        MilestoneActionItemResponse dto = new MilestoneActionItemResponse();
        dto.setId(item.getId());
        dto.setText(item.getText());
        dto.setCompleted(item.isCompleted());
        dto.setOrderIndex(item.getOrderIndex());
        dto.setCompletedAt(item.getCompletedAt());
        dto.setCreatedById(item.getCreatedBy().getId());
        if (item.getCompletedBy() != null) {
            dto.setCompletedById(item.getCompletedBy().getId());
        }
        dto.setCreatedAt(item.getCreatedAt());
        return dto;
    }

    private void ensureWithinMentorship(Mentorship mentorship, OffsetDateTime targetDate) {
        OffsetDateTime mentorshipStart = mentorship.getStartDate();
        OffsetDateTime mentorshipEnd = mentorship.getEndDate();
        if (targetDate.isBefore(mentorshipStart) || targetDate.isAfter(mentorshipEnd)) {
            throw new IllegalArgumentException("Milestone target date must be within the mentorship duration");
        }
    }
}
