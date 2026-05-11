package com.group7.backend.controller.support;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Page/size clamping shared by paginated list endpoints. Extracted from the
 * private copies that previously lived in {@code UserController},
 * {@code MatchingController}, and would otherwise have appeared in
 * {@code MentorPairInboxController} — three is the rule-of-three threshold for
 * extraction, so this consolidates the policy in one place.
 *
 * <p>Clamp behaviour, intentionally permissive (silent rather than 400):
 * <ul>
 *   <li>{@code size < 1 → 1} — the {@code defaultValue = "20"} on
 *       {@code @RequestParam} fires only when the param is absent; an explicit
 *       too-small value falls through to the lower bound.</li>
 *   <li>{@code size > 100 → 100}</li>
 *   <li>{@code page < 0 → 0}</li>
 * </ul>
 *
 * <p>If a future endpoint needs different bounds, prefer adding a parameterised
 * variant rather than re-introducing per-controller copies.
 */
public final class PageableSupport {

    private PageableSupport() {
    }

    public static Pageable clampPageable(int page, int size) {
        int clampedSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(Math.max(page, 0), clampedSize);
    }
}
