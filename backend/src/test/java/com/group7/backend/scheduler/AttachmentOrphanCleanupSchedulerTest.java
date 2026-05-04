package com.group7.backend.scheduler;

import com.group7.backend.entity.Attachment;
import com.group7.backend.entity.Mentor;
import com.group7.backend.entity.User;
import com.group7.backend.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentOrphanCleanupSchedulerTest {

    @Mock private AttachmentRepository attachmentRepository;

    @TempDir Path uploadDir;

    private AttachmentOrphanCleanupScheduler scheduler;
    private final OffsetDateTime now = OffsetDateTime.of(2026, 5, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-05-01T12:00:00Z"), ZoneOffset.UTC);
        scheduler = new AttachmentOrphanCleanupScheduler(attachmentRepository, fixed);
        ReflectionTestUtils.setField(scheduler, "uploadDir", uploadDir.toString());
        ReflectionTestUtils.setField(scheduler, "retentionHours", 24);
    }

    @Test
    void deletesOrphanRowsAndUnderlyingFiles() throws IOException {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        Path fileA = Files.write(uploadDir.resolve(idA + ".pdf"), new byte[]{1, 2, 3});
        Path fileB = Files.write(uploadDir.resolve(idB + ".png"), new byte[]{4, 5, 6});
        when(attachmentRepository.findOrphansOlderThan(any(OffsetDateTime.class)))
                .thenReturn(List.of(orphan(idA, idA + ".pdf"), orphan(idB, idB + ".png")));
        when(attachmentRepository.deleteIfStillOrphan(idA)).thenReturn(1);
        when(attachmentRepository.deleteIfStillOrphan(idB)).thenReturn(1);

        scheduler.sweepOrphans();

        assertThat(Files.exists(fileA)).isFalse();
        assertThat(Files.exists(fileB)).isFalse();
        verify(attachmentRepository).deleteIfStillOrphan(idA);
        verify(attachmentRepository).deleteIfStillOrphan(idB);
    }

    @Test
    void doesNothingWhenNoOrphans() {
        when(attachmentRepository.findOrphansOlderThan(any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());

        scheduler.sweepOrphans();

        verify(attachmentRepository, never()).deleteIfStillOrphan(any(UUID.class));
    }

    @Test
    void leavesFileOnDisk_whenConcurrentMessageReferencesAttachment() throws IOException {
        // Race fix: a POST /messages lands between findOrphansOlderThan and the
        // per-row delete. The conditional delete returns 0 rows, the scheduler
        // skips the file delete, and the just-attached message keeps its FK.
        UUID id = UUID.randomUUID();
        Path file = Files.write(uploadDir.resolve(id + ".pdf"), new byte[]{1, 2, 3});
        when(attachmentRepository.findOrphansOlderThan(any(OffsetDateTime.class)))
                .thenReturn(List.of(orphan(id, id + ".pdf")));
        when(attachmentRepository.deleteIfStillOrphan(id)).thenReturn(0);

        scheduler.sweepOrphans();

        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void mixedSweep_deletesUnreferencedAndKeepsRacedSideBySide() throws IOException {
        // Two orphans surface in the same sweep. The first race-loses (a
        // message just referenced it); the second is genuinely abandoned.
        // The scheduler must process them independently — it must not abort
        // the whole sweep on a single skip.
        UUID raced = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        Path racedFile = Files.write(uploadDir.resolve(raced + ".pdf"), new byte[]{1});
        Path staleFile = Files.write(uploadDir.resolve(stale + ".pdf"), new byte[]{2});
        when(attachmentRepository.findOrphansOlderThan(any(OffsetDateTime.class)))
                .thenReturn(List.of(orphan(raced, raced + ".pdf"), orphan(stale, stale + ".pdf")));
        when(attachmentRepository.deleteIfStillOrphan(raced)).thenReturn(0);
        when(attachmentRepository.deleteIfStillOrphan(stale)).thenReturn(1);

        scheduler.sweepOrphans();

        assertThat(Files.exists(racedFile)).isTrue();
        assertThat(Files.exists(staleFile)).isFalse();
    }

    @Test
    void survivesMissingFileOnDisk() {
        // Row exists in DB but file is already gone (e.g. previous sweep
        // partially completed). The sweep must still drop the orphan row.
        UUID idA = UUID.randomUUID();
        when(attachmentRepository.findOrphansOlderThan(any(OffsetDateTime.class)))
                .thenReturn(List.of(orphan(idA, idA + ".pdf")));
        when(attachmentRepository.deleteIfStillOrphan(idA)).thenReturn(1);

        scheduler.sweepOrphans();

        verify(attachmentRepository).deleteIfStillOrphan(idA);
    }

    @Test
    void refusesToDeleteFilesOutsideUploadDir() throws IOException {
        // Pathological filename that would resolve outside the upload dir.
        UUID idA = UUID.randomUUID();
        Path outsiderDir = Files.createTempDirectory("outsider");
        Path outsider = Files.write(outsiderDir.resolve("evil.pdf"), new byte[]{9});
        try {
            String escapedFilename = "../" + outsiderDir.getFileName() + "/evil.pdf";
            when(attachmentRepository.findOrphansOlderThan(any(OffsetDateTime.class)))
                    .thenReturn(List.of(orphan(idA, escapedFilename)));
            when(attachmentRepository.deleteIfStillOrphan(idA)).thenReturn(1);

            scheduler.sweepOrphans();

            assertThat(Files.exists(outsider)).isTrue();
        } finally {
            Files.deleteIfExists(outsider);
            Files.deleteIfExists(outsiderDir);
        }
    }

    private Attachment orphan(UUID id, String filename) {
        User user = new Mentor();
        user.setId(99L);
        Attachment a = new Attachment();
        a.setId(id);
        a.setFilename(filename);
        a.setContentType("application/pdf");
        a.setSizeBytes(3);
        a.setUploader(user);
        a.setCreatedAt(now.minusDays(2));
        return a;
    }
}
