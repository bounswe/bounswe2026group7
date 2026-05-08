package com.group7.backend.repository;

import com.group7.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Returns all non-admin users (mentors + mentees). Excludes admins from
     * any caller that surfaces user listings to mentors/mentees.
     */
    @Query("select u from User u where type(u) <> com.group7.backend.entity.Admin")
    Page<User> findAllNonAdmins(Pageable pageable);

    /**
     * Returns the ids of every admin user (#135). Used by the report
     * fan-out to publish a {@code REPORT_RECEIVED} notification per admin.
     * Returns ids only — no need to materialise full {@code Admin} rows for
     * a notification fan-out.
     *
     * <p>Admin churn is rare (single-digit count in practice) so an
     * unbounded read is fine. If admin count grows past dozens, refactor
     * the fan-out to a single broadcast event with internal in-listener
     * iteration.
     */
    @Query("select u.id from User u where type(u) = com.group7.backend.entity.Admin")
    List<Long> findAllAdminIds();
}
