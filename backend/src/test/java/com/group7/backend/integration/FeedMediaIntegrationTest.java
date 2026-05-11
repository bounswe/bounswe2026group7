package com.group7.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.group7.backend.dto.request.LoginRequest;
import com.group7.backend.dto.request.RegisterRequest;
import com.group7.backend.entity.User;
import com.group7.backend.repository.UserRepository;
import com.group7.backend.repository.VerificationTokenRepository;
import com.group7.backend.scheduler.AttachmentOrphanCleanupScheduler;
import com.group7.backend.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for the feed image-attachment surface (#485) against
 * real Postgres. Walks the full path: upload via the shared
 * {@code /api/messages/attachments} endpoint → attach at post create →
 * download via the feed-scoped endpoint → soft-delete preserves junction
 * rows → orphan-cleanup scheduler skips feed-referenced attachments.
 *
 * <p>Negative paths cover the five rejection modes the spec calls out (5+
 * attachments, non-uploader, non-image content type, duplicate ids in the
 * request, attaching an id already owned by another post) plus the
 * cross-controller probe (chat-only id rejected with 404 on the
 * feed-media path) and the unauthenticated baseline.
 *
 * <p>Test profile disables rate-limiting so the smoke test is not coupled
 * to bucket capacities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedMediaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private VerificationTokenRepository verificationTokenRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AttachmentOrphanCleanupScheduler orphanCleanupScheduler;
    @MockitoBean private EmailService emailService;

    @BeforeEach
    void cleanDb() {
        // Children first by FK, then parents. feed_post_attachments references
        // both feed_posts.id (ON DELETE CASCADE) and attachments.id (NO ACTION) —
        // truncating in reverse-FK order keeps the cleanup deterministic across
        // schedule reruns and avoids deferred-constraint surprises.
        jdbcTemplate.update("DELETE FROM feed_post_attachments");
        jdbcTemplate.update("DELETE FROM feed_post_hashtags");
        jdbcTemplate.update("DELETE FROM feed_posts");
        // Messages reference attachments via SET NULL; clearing messages first
        // ensures attachment-row deletes do not orphan a message_id reference.
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM attachments");
        verificationTokenRepository.deleteAll();
        userRepository.deleteAll();
        doNothing().when(emailService).sendVerificationEmail(any(), anyString());
    }

    // ── 1. Happy path: single image post ────────────────────────────────────

    @Test
    void singleImagePost_create_download_succeeds() throws Exception {
        String token = registerAndLogin("alice@test.com");
        UUID attachmentId = uploadPng(token, "alice.png");

        // Create a post with the image attached
        MvcResult createResult = mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "first image post",
                                "hashtags", List.of("test"),
                                "attachmentIds", List.of(attachmentId.toString())))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments").isArray())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].id").value(attachmentId.toString()))
                .andExpect(jsonPath("$.attachments[0].contentType").value("image/png"))
                .andExpect(jsonPath("$.attachments[0].downloadUrl")
                        .value(org.hamcrest.Matchers.containsString(
                                "/api/uploads/feed-media/" + attachmentId)))
                .andReturn();
        long postId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();
        assertThat(postId).isPositive();

        // Download via the feed-scoped endpoint
        mockMvc.perform(get("/api/uploads/feed-media/" + attachmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Disposition"));
    }

    // ── 2. Happy path: 4 images, ordering preserved ─────────────────────────

    @Test
    void fourImagePost_preservesOrder() throws Exception {
        String token = registerAndLogin("bob@test.com");
        UUID a0 = uploadPng(token, "a0.png");
        UUID a1 = uploadPng(token, "a1.png");
        UUID a2 = uploadPng(token, "a2.png");
        UUID a3 = uploadPng(token, "a3.png");
        List<String> orderedIds = List.of(a0.toString(), a1.toString(), a2.toString(), a3.toString());

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "four images",
                                "attachmentIds", orderedIds))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments.length()").value(4))
                .andExpect(jsonPath("$.attachments[0].id").value(a0.toString()))
                .andExpect(jsonPath("$.attachments[1].id").value(a1.toString()))
                .andExpect(jsonPath("$.attachments[2].id").value(a2.toString()))
                .andExpect(jsonPath("$.attachments[3].id").value(a3.toString()));
    }

    // ── 3. Reject 5+ attachments at the DTO boundary ────────────────────────

    @Test
    void fiveAttachments_rejectedAt400() throws Exception {
        String token = registerAndLogin("carol@test.com");
        // Random UUIDs — they won't be reached because @Size(max = 4) fires
        // at Bean Validation before the service even loads the rows.
        List<String> ids = List.of(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "too many",
                                "attachmentIds", ids))))
                .andExpect(status().isBadRequest());
    }

    // ── 4. Reject non-uploader attachment (provenance gate) ─────────────────

    @Test
    void nonUploaderAttachment_rejectedAt403() throws Exception {
        String aliceToken = registerAndLogin("dave@test.com");
        String bobToken = registerAndLogin("eve@test.com");
        UUID aliceUpload = uploadPng(aliceToken, "alice.png");

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "stealing alice's upload",
                                "attachmentIds", List.of(aliceUpload.toString())))))
                .andExpect(status().isForbidden());
    }

    // ── 5. Reject non-image content type ────────────────────────────────────

    @Test
    void nonImageAttachment_rejectedAt400() throws Exception {
        String token = registerAndLogin("frank@test.com");
        UUID pdfId = uploadPdf(token, "doc.pdf");

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "pdf in feed?",
                                "attachmentIds", List.of(pdfId.toString())))))
                .andExpect(status().isBadRequest());
    }

    // ── 6. DB UNIQUE on attachment_id → 409 on second-post attach ───────────

    @Test
    void attachmentAlreadyOnAnotherPost_rejectedAt409() throws Exception {
        String token = registerAndLogin("grace@test.com");
        UUID attachmentId = uploadPng(token, "shared.png");

        // Post 1 owns the attachment.
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "first owner",
                                "attachmentIds", List.of(attachmentId.toString())))))
                .andExpect(status().isCreated());

        // Post 2 tries to claim the same id — DB UNIQUE fires.
        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "second owner",
                                "attachmentIds", List.of(attachmentId.toString())))))
                .andExpect(status().isConflict());
    }

    // ── 7. Reject duplicate id in the same request ──────────────────────────

    @Test
    void duplicateAttachmentIdInRequest_rejectedAt400() throws Exception {
        String token = registerAndLogin("heidi@test.com");
        UUID attachmentId = uploadPng(token, "dup.png");

        mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "dup ids",
                                "attachmentIds", List.of(
                                        attachmentId.toString(), attachmentId.toString())))))
                .andExpect(status().isBadRequest());
    }

    // ── 8. Chat-only attachment 404s on the feed-media endpoint ─────────────

    @Test
    void chatOnlyAttachment_404OnFeedMediaPath() throws Exception {
        String token = registerAndLogin("ivan@test.com");
        UUID pdfId = uploadPdf(token, "doc.pdf");

        mockMvc.perform(get("/api/uploads/feed-media/" + pdfId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── 9. Anonymous request is rejected by Spring Security ─────────────────

    @Test
    void anonymousFeedMediaDownload_isRejectedBySecurity() throws Exception {
        // The project's JwtAuthenticationFilter + default access-denied handler
        // surfaces missing/invalid credentials as 403 (not 401) — the existing
        // chat AttachmentDownloadController tests follow the same convention.
        // Either status means "you cannot have this resource without auth";
        // 403 is what the codebase emits today.
        UUID someUuid = UUID.randomUUID();
        mockMvc.perform(get("/api/uploads/feed-media/" + someUuid))
                .andExpect(status().isForbidden());
    }

    // ── 10. Soft-delete preserves junction + attachment rows ────────────────

    @Test
    void softDeletePost_preservesAttachmentJunction() throws Exception {
        String token = registerAndLogin("judy@test.com");
        UUID attachmentId = uploadPng(token, "keep.png");

        long postId = createPostWithAttachment(token, "keep me", attachmentId);

        // Soft-delete the post.
        mockMvc.perform(delete("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Integer junctionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_attachments WHERE post_id = ?",
                Integer.class, postId);
        assertThat(junctionRows).isEqualTo(1);

        // Attachment row also survives.
        Integer attachmentRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attachments WHERE id = ?",
                Integer.class, attachmentId);
        assertThat(attachmentRows).isEqualTo(1);
    }

    // ── 11. PATCH replaces attachment set (clear + addAll semantics) ────────

    @Test
    void patchAttachmentIds_replacesSet() throws Exception {
        String token = registerAndLogin("kate@test.com");
        UUID firstId = uploadPng(token, "first.png");
        UUID secondId = uploadPng(token, "second.png");

        long postId = createPostWithAttachment(token, "before patch", firstId);

        // Clear all attachments.
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "attachmentIds", List.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(0));

        // Reattach a different image.
        mockMvc.perform(patch("/api/feed/posts/" + postId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "attachmentIds", List.of(secondId.toString())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].id").value(secondId.toString()));

        // First id no longer referenced from the post → DB has no junction row.
        Integer firstStillLinked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feed_post_attachments WHERE attachment_id = ?",
                Integer.class, firstId);
        assertThat(firstStillLinked).isZero();
    }

    // ── 12. Orphan-cleanup skips feed-referenced attachments ────────────────

    @Test
    void orphanCleanup_skipsFeedReferencedAttachments() throws Exception {
        String token = registerAndLogin("leo@test.com");
        UUID linkedId = uploadPng(token, "linked.png");
        UUID orphanId = uploadPng(token, "orphan.png");

        createPostWithAttachment(token, "linked", linkedId);

        // Age both attachments past the retention window so the sweeper picks
        // them up. The query predicate is created_at < cutoff; the cutoff is
        // now() - retentionHours, so dating both 25 hours in the past makes
        // them eligible (or eligible-modulo-references).
        jdbcTemplate.update(
                "UPDATE attachments SET created_at = NOW() - INTERVAL '25 hours' WHERE id IN (?, ?)",
                linkedId, orphanId);

        orphanCleanupScheduler.sweepOrphans();

        Integer linkedRemaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attachments WHERE id = ?", Integer.class, linkedId);
        assertThat(linkedRemaining)
                .as("Feed-referenced attachment must survive the sweep")
                .isEqualTo(1);

        Integer orphanRemaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attachments WHERE id = ?", Integer.class, orphanId);
        assertThat(orphanRemaining)
                .as("Truly unreferenced attachment must be reclaimed")
                .isZero();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private long createPostWithAttachment(String token, String body, UUID attachmentId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/feed/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", body,
                                "attachmentIds", List.of(attachmentId.toString())))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private UUID uploadPng(String token, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "image/png", pngBody());
        MvcResult res = mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asText());
    }

    private UUID uploadPdf(String token, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "application/pdf", pdfBody());
        MvcResult res = mockMvc.perform(multipart("/api/messages/attachments")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asText());
    }

    private String registerAndLogin(String email) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setFirstName("Feed");
        req.setLastName("Tester");
        req.setEmail(email);
        req.setPassword("Password1");
        req.setIsMentor(true);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        String verifyToken = verificationTokenRepository
                .findByUserIdAndUsedFalse(user.getId()).get(0).getToken();
        mockMvc.perform(get("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("Password1");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("sessionToken").asText();
    }

    /**
     * Minimal PNG body that satisfies {@code AttachmentStorageService}'s
     * magic-byte check: 8-byte PNG signature + IHDR chunk header. The
     * validator inspects only the leading bytes, so a stub like this passes
     * without being a fully-valid renderable PNG.
     */
    private static byte[] pngBody() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R',
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
                0x00, 0x00, 0x00, 0x0D, 'I', 'D', 'A', 'T',
                0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }

    private static byte[] pdfBody() {
        byte[] header = new byte[]{0x25, 0x50, 0x44, 0x46};  // %PDF
        byte[] tail = "payload".getBytes();
        byte[] result = new byte[header.length + tail.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(tail, 0, result, header.length, tail.length);
        return result;
    }
}
