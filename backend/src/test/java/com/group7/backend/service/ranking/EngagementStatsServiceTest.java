package com.group7.backend.service.ranking;

import com.group7.backend.repository.EngagementStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngagementStatsServiceTest {

    private static final OffsetDateTime SINCE =
            OffsetDateTime.of(2026, 4, 11, 0, 0, 0, 0, ZoneOffset.UTC);

    @Mock private EngagementStatsRepository repo;

    @Test
    void emptyIds_returnsEmptyMap_andSkipsRepoCall() {
        var svc = new EngagementStatsService(repo);
        assertThat(svc.statsFor(Set.of(), SINCE)).isEmpty();
        verify(repo, never()).aggregateByAuthor(anyCollection(), any());
    }

    @Test
    void nullIds_returnsEmptyMap_andSkipsRepoCall() {
        var svc = new EngagementStatsService(repo);
        assertThat(svc.statsFor(null, SINCE)).isEmpty();
        verify(repo, never()).aggregateByAuthor(anyCollection(), any());
    }

    @Test
    void rowsAreMappedByAuthorId() {
        OffsetDateTime active = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        when(repo.aggregateByAuthor(Set.of(7L, 8L), SINCE)).thenReturn(List.of(
                new Object[] { 7L, 5L, 3L, 1L, active },
                new Object[] { 8L, 0L, 2L, 0L, active.plusDays(1) }
        ));

        var svc = new EngagementStatsService(repo);
        var out = svc.statsFor(Set.of(7L, 8L), SINCE);

        assertThat(out).hasSize(2);
        assertThat(out.get(7L)).isEqualTo(new EngagementStats(5, 3, 1, active));
        assertThat(out.get(8L)).isEqualTo(new EngagementStats(0, 2, 0, active.plusDays(1)));
    }

    @Test
    void timestampColumn_isNormalizedToOffsetDateTime() {
        Timestamp ts = Timestamp.from(SINCE.toInstant());
        when(repo.aggregateByAuthor(Set.of(7L), SINCE))
                .thenReturn(List.<Object[]>of(new Object[] { 7L, 1L, 0L, 0L, ts }));

        var svc = new EngagementStatsService(repo);
        var out = svc.statsFor(Set.of(7L), SINCE);

        assertThat(out.get(7L).lastActive()).isEqualTo(SINCE);
    }

    @Test
    void instantColumn_isNormalizedToOffsetDateTime() {
        when(repo.aggregateByAuthor(Set.of(7L), SINCE))
                .thenReturn(List.<Object[]>of(new Object[] { 7L, 1L, 0L, 0L, SINCE.toInstant() }));

        var svc = new EngagementStatsService(repo);
        assertThat(svc.statsFor(Set.of(7L), SINCE).get(7L).lastActive()).isEqualTo(SINCE);
    }

    @Test
    void unexpectedColumnType_throwsExplicitly() {
        when(repo.aggregateByAuthor(Set.of(7L), SINCE))
                .thenReturn(List.<Object[]>of(new Object[] { 7L, 1L, 0L, 0L, "not-a-timestamp" }));

        var svc = new EngagementStatsService(repo);
        assertThatThrownBy(() -> svc.statsFor(Set.of(7L), SINCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected last_active column type");
    }

    @Test
    void nullLastActive_passesThrough() {
        when(repo.aggregateByAuthor(Set.of(7L), SINCE))
                .thenReturn(List.<Object[]>of(new Object[] { 7L, 1L, 0L, 0L, null }));
        var svc = new EngagementStatsService(repo);
        assertThat(svc.statsFor(Set.of(7L), SINCE).get(7L).lastActive()).isNull();
    }
}
