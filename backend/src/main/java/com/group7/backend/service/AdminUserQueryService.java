package com.group7.backend.service;

import com.group7.backend.dto.request.AdminUserBanStatusFilter;
import com.group7.backend.dto.request.AdminUserRoleFilter;
import com.group7.backend.dto.response.AdminUserDetailResponse;
import com.group7.backend.dto.response.AdminUserListItem;
import com.group7.backend.entity.User;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Read model for the admin user-management panel (#569).
 *
 * <p>Lives in {@code com.group7.backend.service} so it can reuse the
 * package-private {@link SearchNormaliser#keyword(String)} helper. Carved
 * out of {@code UserService} on purpose: the read shape (admin-only,
 * role + ban-status + bot flag) is materially different from the public
 * user-facing read paths, and folding it into {@code UserService} would
 * blur the admin opacity boundary the rest of that class is designed to
 * preserve.
 *
 * <p>Both queries are transactional read-only: no entity mutations occur
 * along these paths, and the {@code readOnly = true} hint lets Hibernate
 * skip dirty-checking on the loaded entity in {@link #getUserDetail}.
 */
@Service
public class AdminUserQueryService {

    private final UserRepository userRepository;
    private final BanService banService;
    private final Clock clock;

    public AdminUserQueryService(UserRepository userRepository,
                                 BanService banService,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.banService = banService;
        this.clock = clock;
    }

    /**
     * Lists users for the admin panel. The {@code role} and
     * {@code banStatus} enum filters are independently optional; the
     * {@code q} keyword is normalised via
     * {@link SearchNormaliser#keyword(String)} (so inputs shorter than 3
     * alphanumerics fall back to "no keyword filter" rather than triggering
     * a pg_trgm seq-scan).
     *
     * <p>Ordering is newest-first ({@code createdAt desc, id desc}); the
     * id tiebreaker keeps pagination deterministic when fixture data
     * shares a timestamp.
     */
    @Transactional(readOnly = true)
    public Page<AdminUserListItem> listUsers(AdminUserRoleFilter role,
                                             AdminUserBanStatusFilter banStatus,
                                             String q,
                                             Pageable pageable) {
        Boolean roleMentor = role == AdminUserRoleFilter.MENTOR ? Boolean.TRUE : null;
        Boolean roleMentee = role == AdminUserRoleFilter.MENTEE ? Boolean.TRUE : null;
        Boolean roleAdmin  = role == AdminUserRoleFilter.ADMIN  ? Boolean.TRUE : null;

        Boolean banActive = switch (banStatus == null ? null : banStatus) {
            case ACTIVE -> Boolean.TRUE;
            case NONE   -> Boolean.FALSE;
            case null   -> null;
        };

        String keyword = SearchNormaliser.keyword(q);

        return userRepository.findAdminUsers(
                roleMentor, roleMentee, roleAdmin,
                banActive, keyword,
                OffsetDateTime.now(clock),
                pageable);
    }

    /**
     * Returns the admin drill-down view for a single user: profile +
     * full ban history (newest first) + spam flag. Reuses
     * {@link BanService#listBansForUser(Long)} for the history so the
     * audit ordering stays consistent with {@code GET /api/admin/bans/users/{userId}}.
     *
     * @throws ResourceNotFoundException 404 — no user with the given id
     */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + id));
        return AdminUserDetailResponse.from(user, banService.listBansForUser(id));
    }
}
