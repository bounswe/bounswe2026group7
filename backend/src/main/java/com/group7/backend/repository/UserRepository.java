package com.group7.backend.repository;

import com.group7.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * Candidate window for the follow-recommendation pipeline (#344). Excludes
     * admins, the viewer themselves, and anyone the viewer already follows in
     * a single SQL pass; the ranker scores the returned slice in memory and
     * the service slices the resulting page. Order is {@code id DESC} —
     * deterministic with no semantic signal — mirroring the matching pipeline
     * (#262/#273) where the same trade-off applies: pages beyond the window
     * return empty content.
     */
    @Query("""
            select u from User u
            where type(u) <> com.group7.backend.entity.Admin
              and u.id <> :viewerId
              and not exists (
                  select 1 from Follow f
                  where f.id.followerId = :viewerId
                    and f.id.followeeId = u.id)
            order by u.id desc
            """)
    List<User> findFollowRecommendationCandidates(@Param("viewerId") Long viewerId,
                                                  Pageable pageable);
}
