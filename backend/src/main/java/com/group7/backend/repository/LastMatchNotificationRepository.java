package com.group7.backend.repository;

import com.group7.backend.entity.LastMatchNotification;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link LastMatchNotification}. The entity's PK is
 * the recipient {@code user_id}, so {@code findById}/{@code save} are sufficient
 * for the scheduler's read-compare-upsert flow — no custom queries needed.
 */
public interface LastMatchNotificationRepository
        extends JpaRepository<LastMatchNotification, Long> {
}
