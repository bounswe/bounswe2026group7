package com.group7.backend.repository.projection;

/**
 * Projection target for grouped action-item count queries on
 * {@code MeetingActionItemRepository} and {@code MilestoneActionItemRepository}.
 *
 * <p>Both repositories use a JPQL constructor expression
 * ({@code SELECT new ActionItemCountTuple(...)}) so the service layer never has to
 * unpack a {@code List<Object[]>}.
 */
public record ActionItemCountTuple(
        Long parentId,
        Long total,
        Long completed
) {
}
