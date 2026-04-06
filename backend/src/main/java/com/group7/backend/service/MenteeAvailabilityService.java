package com.group7.backend.service;

import com.group7.backend.dto.request.AvailabilitySlotRequest;
import com.group7.backend.dto.response.MenteeAvailabilitySlotResponse;
import com.group7.backend.entity.Mentee;
import com.group7.backend.entity.MenteeAvailabilitySlot;
import com.group7.backend.exception.OverlappingSlotException;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.MenteeAvailabilitySlotRepository;
import com.group7.backend.repository.MenteeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

@Service
public class MenteeAvailabilityService {

    private final MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository;
    private final MenteeRepository menteeRepository;

    public MenteeAvailabilityService(MenteeAvailabilitySlotRepository menteeAvailabilitySlotRepository,
                                     MenteeRepository menteeRepository) {
        this.menteeAvailabilitySlotRepository = menteeAvailabilitySlotRepository;
        this.menteeRepository = menteeRepository;
    }

    @Transactional(readOnly = true)
    public List<MenteeAvailabilitySlotResponse> getSlots(Long menteeId) {
        menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        return menteeAvailabilitySlotRepository.findByMenteeId(menteeId).stream()
                .sorted(Comparator.comparing(MenteeAvailabilitySlot::getDayOfWeek)
                        .thenComparing(MenteeAvailabilitySlot::getStartTime))
                .map(MenteeAvailabilitySlotResponse::from)
                .toList();
    }

    @Transactional
    public List<MenteeAvailabilitySlotResponse> bulkUpdate(Long menteeId, List<AvailabilitySlotRequest> slots) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        validateNoOverlapsInSet(slots);

        menteeAvailabilitySlotRepository.deleteByMenteeId(menteeId);

        List<MenteeAvailabilitySlot> saved = slots.stream().map(req -> {
            MenteeAvailabilitySlot entity = new MenteeAvailabilitySlot();
            entity.setMentee(mentee);
            entity.setDayOfWeek(req.getDayOfWeek());
            entity.setStartTime(req.getStartTime());
            entity.setEndTime(req.getEndTime());
            entity.setRecurring(req.getRecurring() != null ? req.getRecurring() : true);
            return menteeAvailabilitySlotRepository.save(entity);
        }).toList();

        return saved.stream()
                .sorted(Comparator.comparing(MenteeAvailabilitySlot::getDayOfWeek)
                        .thenComparing(MenteeAvailabilitySlot::getStartTime))
                .map(MenteeAvailabilitySlotResponse::from)
                .toList();
    }

    @Transactional
    public MenteeAvailabilitySlotResponse addSlot(Long menteeId, AvailabilitySlotRequest request) {
        Mentee mentee = menteeRepository.findById(menteeId)
                .orElseThrow(() -> new ResourceNotFoundException("Mentee not found"));

        List<MenteeAvailabilitySlot> existingOnDay = menteeAvailabilitySlotRepository.findByMenteeId(menteeId).stream()
                .filter(s -> s.getDayOfWeek() == request.getDayOfWeek())
                .toList();

        for (MenteeAvailabilitySlot existing : existingOnDay) {
            if (overlaps(request.getStartTime(), request.getEndTime(),
                    existing.getStartTime(), existing.getEndTime())) {
                throw new OverlappingSlotException("New slot overlaps with existing slot on " + request.getDayOfWeek());
            }
        }

        MenteeAvailabilitySlot entity = new MenteeAvailabilitySlot();
        entity.setMentee(mentee);
        entity.setDayOfWeek(request.getDayOfWeek());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setRecurring(request.getRecurring() != null ? request.getRecurring() : true);

        return MenteeAvailabilitySlotResponse.from(menteeAvailabilitySlotRepository.save(entity));
    }

    @Transactional
    public void removeSlot(Long menteeId, Long slotId) {
        long deleted = menteeAvailabilitySlotRepository.deleteByIdAndMenteeId(slotId, menteeId);
        if (deleted == 0) {
            throw new ResourceNotFoundException("Mentee availability slot not found");
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
