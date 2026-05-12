package com.group7.backend.dto.response;

import com.group7.backend.entity.Admin;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.BeanUtils;

/**
 * Admin profile response. Returned only by the self-view path
 * {@code GET /api/users/me} when the authenticated user is an
 * {@link Admin}.
 *
 * <p>Admin opacity to third parties is preserved upstream:
 * <ul>
 *   <li>{@code UserService.getProfileById} short-circuits with 403 for
 *       admin targets before reaching {@code mapToResponse}.</li>
 *   <li>{@code UserRepository.findAllNonAdmins} filters admins out of
 *       list and search surfaces.</li>
 *   <li>{@code FollowService.checkVisibility} throws 403 on admin
 *       targets for follower / following list endpoints.</li>
 * </ul>
 *
 * <p>Carries no admin-specific fields: the {@link Admin} entity adds
 * none over {@link com.group7.backend.entity.User}, so the wire shape
 * is exactly the common {@link UserResponse} envelope plus
 * {@code role = "ADMIN"}. Keeping the DTO empty is deliberate — if a
 * future change adds an admin-only entity field, surfacing it must be
 * an explicit decision rather than a silent {@code BeanUtils} copy
 * side-effect.
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Admin profile response (self-view only, /me).")
public final class AdminResponse extends UserResponse implements ProfileResponse {

    public static AdminResponse from(Admin admin) {
        AdminResponse response = new AdminResponse();
        BeanUtils.copyProperties(admin, response);
        response.setRole("ADMIN");
        return response;
    }
}
