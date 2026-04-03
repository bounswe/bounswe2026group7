package com.group7.backend.service;

import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.response.AvailabilitySlotResponse;
import com.group7.backend.entity.AvailabilitySlot;
import com.group7.backend.entity.Mentor;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.AvailabilitySlotRepository;
import com.group7.backend.repository.MentorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class AvailabilityService {

    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final MentorRepository mentorRepository;

    public AvailabilityService(AvailabilitySlotRepository availabilitySlotRepository,
                               MentorRepository mentorRepository) {
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.mentorRepository = mentorRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailabilitySlotResponse> getSlots(Long mentorId) {
        mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        return availabilitySlotRepository.findByMentorId(mentorId).stream()
                .sorted(Comparator.comparing(AvailabilitySlot::getDayOfWeek)
                        .thenComparing(AvailabilitySlot::getStartTime))
                .map(AvailabilitySlotResponse::from)
                .toList();
    }

    @Transactional
    public List<AvailabilitySlotResponse> bulkUpdate(Long mentorId, List<AvailabilitySlotRequest> slots) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        validateNoOverlapsInSet(slots);

        availabilitySlotRepository.deleteByMentorId(mentorId);

        List<AvailabilitySlot> saved = slots.stream().map(req -> {
            AvailabilitySlot entity = new AvailabilitySlot();
            entity.setMentor(mentor);
            entity.setDayOfWeek(req.getDayOfWeek());
            entity.setStartTime(req.getStartTime());
            entity.setEndTime(req.getEndTime());
            entity.setRecurring(req.getRecurring() != null ? req.getRecurring() : true);
            return availabilitySlotRepository.save(entity);
        }).toList();

        return saved.stream()
                .sorted(Comparator.comparing(AvailabilitySlot::getDayOfWeek)
                        .thenComparing(AvailabilitySlot::getStartTime))
                .map(AvailabilitySlotResponse::from)
                .toList();
    }

    @Transactional
    public AvailabilitySlotResponse addSlot(Long mentorId, AvailabilitySlotRequest request) {
        Mentor mentor = mentorRepository.findById(mentorId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentor not found"));

        List<AvailabilitySlot> existingOnDay = availabilitySlotRepository.findByMentorId(mentorId).stream()
                .filter(s -> s.getDayOfWeek() == request.getDayOfWeek())
                .toList();

        for (AvailabilitySlot existing : existingOnDay) {
            if (overlaps(request.getStartTime(), request.getEndTime(),
                    existing.getStartTime(), existing.getEndTime())) {
                throw new OverlappingSlotException("New slot overlaps with existing slot on " + request.getDayOfWeek());
            }
        }

        AvailabilitySlot entity = new AvailabilitySlot();
        entity.setMentor(mentor);
        entity.setDayOfWeek(request.getDayOfWeek());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setRecurring(request.getRecurring() != null ? request.getRecurring() : true);

        return AvailabilitySlotResponse.from(availabilitySlotRepository.save(entity));
    }

    @Transactional
    public void removeSlot(Long mentorId, Long slotId) {
        long deleted = availabilitySlotRepository.deleteByIdAndMentorId(slotId, mentorId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Availability slot not found");
        }
    }

    private void validateNoOverlapsInSet(List<AvailabilitySlotRequest> slots) {
        for (int i = 0; i < slots.size(); i++) {
            for (int j = i + 1; j < slots.size(); j++) {
                AvailabilitySlotRequest a = slots.get(i);
                AvailabilitySlotRequest b = slots.get(j);
                if (a.getDayOfWeek() == b.getDayOfWeek()
                        && overlaps(a.getStartTime(), a.getEndTime(), b.getStartTime(), b.getEndTime())) {
                    throw new OverlappingSlotException(
                            "Slots overlap on " + a.getDayOfWeek() + ": "
                                    + a.getStartTime() + "-" + a.getEndTime()
                                    + " and " + b.getStartTime() + "-" + b.getEndTime());
                }
            }
        }
    }

    private boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }
}
