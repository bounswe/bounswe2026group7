package com.group7.backend.docs.feed;

/**
 * Concrete JSON payload examples surfaced through Swagger UI / OpenAPI
 * for the social-feed read and write endpoints (#489).
 *
 * <p>Lives in a single file so frontend and mobile developers can scan
 * one source for shapes, and so payload edits show up as a single PR
 * diff rather than scattered controller edits. Constants are valid,
 * compact-formatted JSON literals — the Swagger renderer pretty-prints
 * them.
 *
 * <p>Examples avoid PII and use the same fake-name conventions as the
 * mentor seed data: "Ada", "Esra", "Selin". Timestamps are deliberately
 * fixed (not "now") so the docs are reproducible regardless of when
 * the spec is regenerated.
 */
public final class FeedApiExamples {

    private FeedApiExamples() {
        // constants holder — no instances
    }

    // ── Read responses (list endpoints) ───────────────────────────────────

    public static final String FOR_YOU_RESPONSE = """
            {
              "content": [
                {
                  "id": 42,
                  "authorId": 17,
                  "authorFirstName": "Ada",
                  "body": "Just wrapped a clinical-research methods workshop — slides in the comments.",
                  "hashtags": ["clinical", "research"],
                  "createdAt": "2026-05-09T10:15:00Z",
                  "likeCount": 12,
                  "commentCount": 3
                },
                {
                  "id": 41,
                  "authorId": 22,
                  "authorFirstName": "Esra",
                  "body": "Embedding-based mentor search beats keyword matching on cold-start queries.",
                  "hashtags": ["ml", "recommendations"],
                  "createdAt": "2026-05-09T09:48:11Z",
                  "likeCount": 7,
                  "commentCount": 1
                }
              ],
              "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false },
              "totalElements": 42,
              "totalPages": 3,
              "first": true,
              "last": false,
              "number": 0,
              "size": 20,
              "numberOfElements": 2,
              "empty": false
            }
            """;

    public static final String FOLLOWING_RESPONSE = """
            {
              "content": [
                {
                  "id": 39,
                  "authorId": 22,
                  "authorFirstName": "Esra",
                  "body": "Looking for a mentee interested in graph-based recommenders. DM me.",
                  "hashtags": ["graphs", "recommendations"],
                  "createdAt": "2026-05-09T08:02:43Z",
                  "likeCount": 4,
                  "commentCount": 2
                }
              ],
              "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false },
              "totalElements": 1,
              "totalPages": 1,
              "first": true,
              "last": true,
              "number": 0,
              "size": 20,
              "numberOfElements": 1,
              "empty": false
            }
            """;

    public static final String SEARCH_RESPONSE = """
            {
              "content": [
                {
                  "id": 42,
                  "authorId": 17,
                  "authorFirstName": "Ada",
                  "body": "Just wrapped a clinical-research methods workshop — slides in the comments.",
                  "hashtags": ["clinical", "research"],
                  "createdAt": "2026-05-09T10:15:00Z",
                  "likeCount": 12,
                  "commentCount": 3
                }
              ],
              "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false },
              "totalElements": 1,
              "totalPages": 1,
              "first": true,
              "last": true,
              "number": 0,
              "size": 20,
              "numberOfElements": 1,
              "empty": false
            }
            """;

    public static final String POSTS_BY_AUTHOR_RESPONSE = """
            {
              "content": [
                {
                  "id": 42,
                  "authorId": 17,
                  "authorFirstName": "Ada",
                  "body": "Just wrapped a clinical-research methods workshop — slides in the comments.",
                  "hashtags": ["clinical", "research"],
                  "createdAt": "2026-05-09T10:15:00Z",
                  "likeCount": 12,
                  "commentCount": 3
                },
                {
                  "id": 38,
                  "authorId": 17,
                  "authorFirstName": "Ada",
                  "body": "Reviewing manuscripts for the systematic-review track.",
                  "hashtags": ["clinical"],
                  "createdAt": "2026-05-07T13:21:08Z",
                  "likeCount": 2,
                  "commentCount": 0
                }
              ],
              "pageable": { "pageNumber": 0, "pageSize": 20, "offset": 0, "paged": true, "unpaged": false },
              "totalElements": 2,
              "totalPages": 1,
              "first": true,
              "last": true,
              "number": 0,
              "size": 20,
              "numberOfElements": 2,
              "empty": false
            }
            """;

    // ── Single-post detail ────────────────────────────────────────────────

    public static final String FEED_POST_RESPONSE = """
            {
              "id": 42,
              "authorId": 17,
              "authorFirstName": "Ada",
              "body": "Just wrapped a clinical-research methods workshop — slides in the comments.",
              "hashtags": ["clinical", "research"],
              "createdAt": "2026-05-09T10:15:00Z",
              "updatedAt": "2026-05-09T11:02:34Z",
              "isEdited": true,
              "isAuthor": false
            }
            """;

    // ── Create / update request bodies ────────────────────────────────────

    public static final String CREATE_FEED_POST_REQUEST = """
            {
              "body": "Excited to share thoughts on data science and graph-based recommenders.",
              "hashtags": ["DataScience", "#Graphs"]
            }
            """;

    // ── Interaction state (toggle responses) ──────────────────────────────

    public static final String TOGGLE_LIKE_RESPONSE_LIKED = """
            {
              "likeCount": 13,
              "commentCount": 3,
              "shareCount": 1,
              "bookmarkCount": 2,
              "viewerHasLiked": true,
              "viewerHasBookmarked": false
            }
            """;

    public static final String TOGGLE_LIKE_RESPONSE_UNLIKED = """
            {
              "likeCount": 12,
              "commentCount": 3,
              "shareCount": 1,
              "bookmarkCount": 2,
              "viewerHasLiked": false,
              "viewerHasBookmarked": false
            }
            """;

    // ── Comment payloads ──────────────────────────────────────────────────

    public static final String ADD_COMMENT_REQUEST = """
            {
              "body": "Great session! Sharing the slides with my cohort, hope that's OK."
            }
            """;

    public static final String FEED_COMMENT_RESPONSE = """
            {
              "id": 101,
              "postId": 42,
              "authorId": 17,
              "authorFirstName": "Ada",
              "body": "Great session! Sharing the slides with my cohort, hope that's OK.",
              "createdAt": "2026-05-09T12:00:00Z",
              "updatedAt": "2026-05-09T12:00:00Z",
              "isEdited": false,
              "isAuthor": true,
              "isDeleted": false
            }
            """;
}
