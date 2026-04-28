package com.group7.backend.repository;

import com.group7.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
