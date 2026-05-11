package com.group7.backend.repository;

import com.group7.backend.entity.UserKeywordMute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserKeywordMuteRepository extends JpaRepository<UserKeywordMute, Long> {

    List<UserKeywordMute> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByUserIdAndKeyword(Long userId, String keyword);

    int countByUserId(Long userId);

    Optional<UserKeywordMute> findByIdAndUserId(Long id, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
