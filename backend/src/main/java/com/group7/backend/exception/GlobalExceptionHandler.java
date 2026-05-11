package com.group7.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(ResourceNotFoundException ex,
                                                                      HttpServletRequest request) {
        log.warn("Not found: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(ProfileNotVisibleException.class)
    public ResponseEntity<Map<String, String>> handleProfileNotVisible(ProfileNotVisibleException ex,
                                                                       HttpServletRequest request) {
        log.warn("Forbidden profile access: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(UserBannedException.class)
    public ResponseEntity<Map<String, Object>> handleUserBanned(UserBannedException ex,
                                                                HttpServletRequest request) {
        com.group7.backend.entity.Ban ban = ex.getBan();
        log.warn("Banned user attempted gated action: method={}, path={}, userId={}, expiresAt={}",
                request.getMethod(), request.getRequestURI(), ban.getUser().getId(), ban.getExpiresAt());
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Forbidden");
        // Stable client-facing code so the frontend can branch on banned vs.
        // generic 403 without parsing the message string. Required by #280:
        // "banned users get 403 BANNED_UNTIL response".
        body.put("code", "BANNED_UNTIL");
        body.put("message", ex.getMessage());
        body.put("reason", ban.getReason());
        body.put("expiresAt", ban.getExpiresAt().toString());
        body.put("banCount", ban.getBanCount());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MentorshipRequestException.class)
    public ResponseEntity<Map<String, String>> handleMentorshipRequest(MentorshipRequestException ex,
                                                                       HttpServletRequest request) {
        log.warn("Mentorship request conflict: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, String>> handleConcurrencyFailure(ConcurrencyFailureException ex,
                                                                        HttpServletRequest request) {
        // Covers ObjectOptimisticLockingFailureException (JPA @Version conflict)
        // and CannotAcquireLockException / DeadlockLoserDataAccessException
        // (Postgres-detected deadlocks during concurrent modification).
        log.warn("Concurrent modification conflict: method={}, path={}, type={}",
                request.getMethod(), request.getRequestURI(), ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict",
                "This action conflicted with a concurrent update. Please retry.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                                            HttpServletRequest request) {
        // Database integrity violations — UNIQUE collisions, FK or CHECK
        // breaches — surface as 409 Conflict. The specific case driving this
        // handler is the {@code feed_post_attachments_unique_attachment}
        // constraint (#485): claiming an attachment id that is already
        // referenced by another feed post. Service-layer pre-checks short-
        // circuit the common case with cleaner messages; this handler is the
        // race-window backstop and the uniform response for any other
        // integrity violation that bubbles past the service.
        log.warn("Data integrity violation: method={}, path={}, mostSpecificCause={}",
                request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict",
                "This action conflicts with the current state of the resource (constraint violation).");
    }

    @ExceptionHandler(OverlappingSlotException.class)
    public ResponseEntity<Map<String, String>> handleOverlappingSlot(OverlappingSlotException ex,
                                                                     HttpServletRequest request) {
        log.warn("Overlapping slot conflict: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(MeetingConflictException.class)
    public ResponseEntity<Map<String, String>> handleMeetingConflict(MeetingConflictException ex,
                                                                     HttpServletRequest request) {
        log.warn("Meeting conflict: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(MilestoneConflictException.class)
    public ResponseEntity<Map<String, String>> handleMilestoneConflict(MilestoneConflictException ex,
                                                                     HttpServletRequest request) {
        log.warn("Milestone conflict: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(InvalidTimelineWindowException.class)
    public ResponseEntity<Map<String, String>> handleInvalidTimelineWindow(InvalidTimelineWindowException ex,
                                                                           HttpServletRequest request) {
        log.warn("Invalid timeline window: method={}, path={}, message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(GoalRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleGoalRequired(GoalRequiredException ex,
                                                                  HttpServletRequest request) {
        log.warn("Goal-required precondition rejected: method={}, path={}, mentorshipId={}",
                request.getMethod(), request.getRequestURI(), ex.getMentorshipId());
        Map<String, Object> body = Map.of(
                "error", "Conflict",
                "code", "GOAL_REQUIRED",
                "message", ex.getMessage(),
                "mentorshipId", ex.getMentorshipId()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationFailed(AuthenticationFailedException ex,
                                                                         HttpServletRequest request) {
        log.warn("Authentication failure: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail(DuplicateEmailException ex,
                                                                    HttpServletRequest request) {
        log.warn("Duplicate email conflict: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidToken(InvalidTokenException ex,
                                                                  HttpServletRequest request) {
        log.warn("Invalid token: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(SpamDetectionException.class)
    public ResponseEntity<Map<String, String>> handleSpamDetection(SpamDetectionException ex,
                                                                   HttpServletRequest request) {
        // Log the specific signal internally but return a generic body so the
        // bot can't fingerprint which check rejected it (#345).
        log.warn("Spam-bot signal rejected request: method={}, path={}, signal={}",
                request.getMethod(), request.getRequestURI(), ex.getSignalType());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", "Request rejected");
    }

    @ExceptionHandler(SelfFollowException.class)
    public ResponseEntity<Map<String, String>> handleSelfFollow(SelfFollowException ex,
                                                                HttpServletRequest request) {
        log.warn("Self-follow rejected: method={}, path={}, message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex,
                                                               HttpServletRequest request) {
        log.warn("Rate limit exceeded: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage());
    }

    @ExceptionHandler(EmailSendException.class)
    public ResponseEntity<Map<String, String>> handleEmailSend(EmailSendException ex,
                                                               HttpServletRequest request) {
        log.error("Email send failure: method={}, path={}", request.getMethod(), request.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Failed to send email. Please try again later.");
    }

    @ExceptionHandler(MatchingNotAllowedException.class)
    public ResponseEntity<Map<String, String>> handleMatchingNotAllowed(MatchingNotAllowedException ex,
                                                                        HttpServletRequest request) {
        log.warn("Matching not allowed: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(TaxonomyUpstreamException.class)
    public ResponseEntity<Map<String, String>> handleTaxonomyUpstream(TaxonomyUpstreamException ex,
                                                                      HttpServletRequest request) {
        log.warn("Taxonomy upstream failure: method={}, path={}, message={}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "Taxonomy provider is unreachable. Please try again.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request",
                "File size exceeds maximum allowed size");
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", "Access denied");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex,
                                                                      HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        log.warn("Validation failed: method={}, path={}, fieldErrorCount={}",
                request.getMethod(), request.getRequestURI(), fieldErrors.size());

        Map<String, Object> body = Map.of("error", "Validation Failed", "messages", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex,
                                                                   HttpServletRequest request) {
        log.warn("Access denied: method={}, path={}, message={}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", "Access denied");
    }

    private ResponseEntity<Map<String, String>> buildErrorResponse(HttpStatus status, String error, String message) {
        Map<String, String> body = Map.of("error", error, "message", message);
        return ResponseEntity.status(status).body(body);
    }
}
