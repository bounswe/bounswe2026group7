package com.group7.backend.repository;

import com.group7.backend.entity.MatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistory, Long> {

    /**
     * Finds the most recent match history record for a given user.
     */
    @Query("SELECT mh FROM MatchHistory mh WHERE mh.userId = ?1 AND mh.userType = ?2 " +
           "ORDER BY mh.calculatedAt DESC LIMIT 1")
    Optional<MatchHistory> findLatestByUserIdAndUserType(Long userId, MatchHistory.UserType userType);
}
