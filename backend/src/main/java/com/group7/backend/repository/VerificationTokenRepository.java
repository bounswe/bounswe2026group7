package com.group7.backend.repository;

import com.group7.backend.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    List<VerificationToken> findByUserIdAndUsedFalse(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, OffsetDateTime since);

    void deleteByExpiresAtBeforeAndUsedFalse(OffsetDateTime now);
}
