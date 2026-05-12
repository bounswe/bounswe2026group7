package com.group7.backend.service;

import com.group7.backend.dto.response.KeywordMuteResponse;
import com.group7.backend.entity.FeedPost;
import com.group7.backend.entity.UserKeywordMute;
import com.group7.backend.exception.ResourceNotFoundException;
import com.group7.backend.repository.UserKeywordMuteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Per-user keyword-mute management plus a post-filter helper used by every
 * feed read path. Keywords are normalised (trim, lowercase) on write and
 * matched case-insensitively as substrings of {@code FeedPost.body} on read.
 */
@Service
@Transactional
public class UserKeywordMuteService {

    /** Cap on muted keywords per user. Keeps the post-filter step cheap. */
    public static final int MAX_KEYWORDS_PER_USER = 50;

    /** Allowed characters after normalisation: lowercase letters, digits,
     *  single internal spaces (collapsed by trim), and hyphens. Excludes
     *  punctuation and Unicode beyond ASCII so the substring match is
     *  predictable across locales. */
    private static final Pattern ALLOWED_KEYWORD = Pattern.compile("^[a-z0-9](?:[a-z0-9 \\-]*[a-z0-9])?$");

    private final UserKeywordMuteRepository repository;

    public UserKeywordMuteService(UserKeywordMuteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<KeywordMuteResponse> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(KeywordMuteResponse::from)
                .toList();
    }

    public KeywordMuteResponse add(Long userId, String rawKeyword) {
        String normalised = normalise(rawKeyword);
        if (repository.countByUserId(userId) >= MAX_KEYWORDS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "keyword-mute cap (" + MAX_KEYWORDS_PER_USER + ") reached");
        }
        if (repository.existsByUserIdAndKeyword(userId, normalised)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "keyword already muted");
        }
        UserKeywordMute saved = repository.save(new UserKeywordMute(userId, normalised));
        return KeywordMuteResponse.from(saved);
    }

    public void delete(Long userId, Long muteId) {
        UserKeywordMute mute = repository.findByIdAndUserId(muteId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("keyword-mute not found with id: " + muteId));
        repository.delete(mute);
    }

    /**
     * Drops every post whose body contains any of the viewer's muted
     * keywords (case-insensitive substring match). Short-circuits to the
     * input list when the viewer is anonymous or has no mutes.
     */
    @Transactional(readOnly = true)
    public List<FeedPost> filter(Long viewerId, List<FeedPost> posts) {
        if (viewerId == null || posts.isEmpty()) {
            return posts;
        }
        if (repository.countByUserId(viewerId) == 0) {
            return posts;
        }
        List<String> mutes = repository.findByUserIdOrderByCreatedAtAsc(viewerId).stream()
                .map(UserKeywordMute::getKeyword)
                .toList();
        return posts.stream()
                .filter(p -> !containsAnyKeyword(p.getBody(), mutes))
                .toList();
    }

    private static boolean containsAnyKeyword(String body, List<String> mutes) {
        if (body == null) {
            return false;
        }
        String lowered = body.toLowerCase(Locale.ROOT);
        for (String mute : mutes) {
            if (lowered.contains(mute)) {
                return true;
            }
        }
        return false;
    }

    private String normalise(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword must not be blank");
        }
        String collapsed = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (collapsed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword must not be blank");
        }
        if (collapsed.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "keyword must be at most 120 characters");
        }
        if (!ALLOWED_KEYWORD.matcher(collapsed).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "keyword must contain only letters, digits, single spaces, and hyphens");
        }
        return collapsed;
    }
}
