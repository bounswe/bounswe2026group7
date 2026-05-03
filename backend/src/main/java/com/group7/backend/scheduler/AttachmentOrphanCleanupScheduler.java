package com.group7.backend.scheduler;

import com.group7.backend.entity.Attachment;
import com.group7.backend.repository.AttachmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Reclaims attachments that were uploaded but never referenced by a message.
 * The retention window ({@code app.upload.orphan-retention-hours}) gives
 * legitimate users time between hitting the upload endpoint and including
 * the {@code attachmentId} in a {@code POST /messages} call.
 *
 * <p>Two failure modes converge here:
 * <ol>
 *   <li>The user uploaded the file but never sent a message (closed the tab,
 *       changed their mind). The file would otherwise sit on disk forever.</li>
 *   <li>The {@code MessageService.send} transaction rolled back AFTER the
 *       attachment row committed but BEFORE the message FK could be
 *       established. The per-upload rollback synchronization in
 *       {@code AttachmentStorageService} guards the file write itself, but
 *       cannot help across a separately-bounded send transaction. This sweep
 *       is the long-tail backstop for that mode.</li>
 * </ol>
 */
@Component
public class AttachmentOrphanCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(AttachmentOrphanCleanupScheduler.class);

    private final AttachmentRepository attachmentRepository;
    private final Clock clock;

    @Value("${app.upload.attachments-dir:/app/uploads/attachments}")
    private String uploadDir;

    @Value("${app.upload.orphan-retention-hours:24}")
    private int retentionHours;

    public AttachmentOrphanCleanupScheduler(AttachmentRepository attachmentRepository, Clock clock) {
        this.attachmentRepository = attachmentRepository;
        this.clock = clock;
    }

    /**
     * Runs daily at 03:15 UTC. The exact minute is staggered off the hour so
     * sweeps don't pile on top of any other top-of-hour scheduled work.
     */
    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @Transactional
    public void sweepOrphans() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(Duration.ofHours(retentionHours));
        List<Attachment> orphans = attachmentRepository.findOrphansOlderThan(cutoff);
        if (orphans.isEmpty()) {
            log.debug("Attachment orphan sweep: no orphans older than {}", cutoff);
            return;
        }

        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        int rowsDeleted = 0;
        int filesDeleted = 0;
        int raced = 0;
        for (Attachment orphan : orphans) {
            // Re-check each candidate at delete time: a POST /messages lands
            // could have referenced this attachment between the find query
            // and now, in which case the conditional delete returns 0 and we
            // leave both the row and its file alone.
            int rows = attachmentRepository.deleteIfStillOrphan(orphan.getId());
            if (rows == 0) {
                raced++;
                continue;
            }
            rowsDeleted += rows;

            Path target = baseDir.resolve(orphan.getFilename()).normalize();
            // Defence in depth: never traverse outside the upload dir even
            // though the filename is server-controlled.
            if (!target.startsWith(baseDir)) {
                log.warn("Refusing to delete outside upload dir: {}", target);
                continue;
            }
            try {
                if (Files.deleteIfExists(target)) {
                    filesDeleted++;
                }
            } catch (IOException e) {
                log.warn("Failed to delete orphan attachment file: {}", target, e);
            }
        }
        log.info("Attachment orphan sweep: deleted {} rows, {} files, skipped {} due to "
                        + "concurrent message references (cutoff={})",
                rowsDeleted, filesDeleted, raced, cutoff);
    }
}
