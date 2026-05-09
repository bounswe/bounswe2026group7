package com.group7.backend.repository;

import com.group7.backend.entity.LastFeedReadAt;
import com.group7.backend.entity.Mentee;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage for the {@link LastFeedReadAt} entity, the
 * {@link LastFeedReadAtRepository#markRead} native upsert, and the V26
 * migration's schema invariants (CASCADE on user delete, PK-driven
 * idempotency, server-side {@code NOW()} timing).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LastFeedReadAtRepositoryTest {

    @Autowired private LastFeedReadAtRepository repository;
    @Autowired private MenteeRepository menteeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        repository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void markRead_insertsRow_whenCursorIsAbsent() {
        Mentee m = saveMentee("lfra_insert", "A");

        int affected = repository.markRead(m.getId());

        assertThat(affected).isEqualTo(1);
        Optional<LastFeedReadAt> row = repository.findById(m.getId());
        assertThat(row).isPresent();
        assertThat(row.get().getLastReadAt()).isNotNull();
    }

    @Test
    void markRead_updatesRow_whenCursorIsPresent() throws InterruptedException {
        Mentee m = saveMentee("lfra_update", "B");

        repository.markRead(m.getId());
        OffsetDateTime first = repository.findById(m.getId()).orElseThrow().getLastReadAt();

        // Sleep just long enough that NOW() advances. Postgres TIMESTAMPTZ
        // resolution is microseconds so 5ms is more than enough on every
        // OS, while still keeping the test fast.
        Thread.sleep(5);
        entityManager.clear();

        repository.markRead(m.getId());
        OffsetDateTime second = repository.findById(m.getId()).orElseThrow().getLastReadAt();

        assertThat(second).isAfter(first);
    }

    @Test
    void markRead_isIdempotentWithRespectToRowCount() {
        Mentee m = saveMentee("lfra_idem", "C");

        repository.markRead(m.getId());
        repository.markRead(m.getId());
        repository.markRead(m.getId());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM last_feed_read_at WHERE user_id = ?",
                Long.class, m.getId());
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void cascadeOnUserDelete_dropsTheCursor() {
        Mentee m = saveMentee("lfra_cascade", "D");
        repository.markRead(m.getId());
        assertThat(repository.findById(m.getId())).isPresent();

        Long userId = m.getId();
        menteeRepository.delete(m);
        entityManager.flush();
        entityManager.clear();

        // Query through JdbcTemplate so the L1 cache is bypassed.
        Long remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM last_feed_read_at WHERE user_id = ?",
                Long.class, userId);
        assertThat(remaining).isZero();
    }

    @Test
    void markRead_throwsForUnknownUserId_dueToForeignKey() {
        Long ghostUserId = 9_999_999L;

        assertThatThrownBy(() -> {
            repository.markRead(ghostUserId);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findById_returnsEmpty_whenCursorWasNeverSet() {
        Mentee m = saveMentee("lfra_absent", "E");

        Optional<LastFeedReadAt> row = repository.findById(m.getId());

        assertThat(row).isEmpty();
    }

    private Mentee saveMentee(String suffix, String firstName) {
        Mentee m = new Mentee();
        m.setFirstName(firstName);
        m.setLastName("L");
        m.setEmail(suffix + "@example.com");
        m.setPasswordHash("x");
        m.setIsEmailVerified(true);
        return menteeRepository.save(m);
    }
}
