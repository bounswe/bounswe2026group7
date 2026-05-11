# Social Feed — backend issue drafts

Ten issue drafts covering the prioritised backend gaps in the social feed. Each file is a ready-to-file issue body; titles and labels are listed at the top of each file for direct paste into `gh issue create`.

## Filing order

| # | Title | Labels | Depends on | Migration |
|---|---|---|---|---|
| 01 | Social Feed — Surface accurate interaction counts in list responses | `backend`, `bug` | — | — |
| 02 | Social Feed — Engagement notifications (likes, comments, shares, new followers) | `backend`, `feature` | — | V38 |
| 03 | Social Feed — Comment likes | `backend`, `feature` | 01 | V39 |
| 04 | Social Feed — Enhanced sharing (quote commentary and repost fanout) | `backend`, `feature` | 02 | V40 |
| 05 | Social Feed — Image attachments on posts | `backend`, `feature` | — | V41 |
| 06 | Social Feed — Search filters (date range, language) and viewer keyword-mute | `backend`, `enhancement` | — | V42 |
| 07 | Social Feed — Soft-delete lifecycle (retention, undelete, edit history, trending cache) | `backend`, `enhancement` | — | V43, V44, V45 |
| 08 | Social Feed — Unit and web-slice test backfill | `backend`, `unit-tests`, `testing` | 01 | — |
| 09 | Social Feed — API ergonomics (OpenAPI examples, total-count header, ETag, comment permalink) | `backend`, `enhancement` | — | — |
| 10 | Social Feed — Standards coverage (Activity Streams 2.0 + Schema.org + JSON-LD pagination + post language) | `backend`, `feature`, `wiki` | — | V46 |

Sub-issue of: **#351 Social Feed — Implementation Umbrella** (file 10 under that umbrella; others may be filed independently).

## Filing checklist

For each issue:

1. Open the file under `feed-issues/NN-...md`.
2. Copy the title from the first header.
3. Copy the labels from the metadata block.
4. Run `gh issue create --title "..." --label "..." --body "$(cat NN-...md | sed -n '/^## Description/,$p')"` (or paste manually).
5. If issue 10, link as a sub-issue of #351 via the sub-issues panel.

## Migration sequencing

Latest migration on `dev` is **V37** (`add_ban_source`). New migrations land in order starting at **V38**. If any issue here lands out of order, renumber the migration in that PR to the next available number.

**Note:** the Advanced For-You feed recommendation PR (#438) claims **V47** (`create_viewer_hashtag_engagement`) and **V48** (`feed_engagement_user_created_indexes`), skipping past V38–V46 reserved by these drafts so the drafts can land at their currently-reserved numbers without renumbering. The drafts 02–11 retain V38–V46.

## Already-merged context that shaped these drafts

- **#474** `feat(feed): add author-based feed posts endpoint` — `GET /api/feed/users/{authorId}/posts` already exists on `dev`. Issue 06 therefore covers only date-range + language filters and the keyword-mute feature; the per-author timeline use case is satisfied by the merged endpoint.
- **#459** added the new mentorship-lifecycle and reminder `NotificationType` values; the four feed engagement types in Issue 02 (`FEED_LIKE`, `FEED_COMMENT`, `FEED_SHARE`, `NEW_FOLLOWER`) are still missing and remain in scope.
- **#445 / #446** are frontend-only feed PRs (scaffold edit/delete UI; share/bookmark UI) — they don't touch backend schemas or services.
- Interaction-service correctness items that previously occupied Issue 07 (visibility gate, comment-body validation) are **already present on `dev`** — `FeedInteractionService.requireVisiblePost` is called from every mutator, and `FeedCommentRequest` carries `@NotBlank` + `@Size(max=1000)`. The known toggle-race is documented in the `toggleLike` javadoc as an explicit design decision. No issue filed for that bucket.

## Conventions used

- Em-dash title separator: `Social Feed — Subject` (matches #347/#348/#349/#350/#351).
- Each issue uses Description / Goal / Deliverables / Implementation notes / Acceptance criteria / Related.
- Code snippets, SQL, and JSON examples are illustrative — final shape decided in PR review.
- All ESCO/Wikidata/ISCED-F references use the real linked-data IRIs the codebase already adopts.
- No issue body uses Turkish text; institution names and example content are written in English.

## API engineering conventions enforced across these issues

These hold for every issue that adds or modifies an endpoint:

- **Backward compatibility first.** No issue removes, renames, or repurposes an endpoint that exists on `dev`. New behaviour ships on new endpoints or as additive payload fields. The existing `POST /posts/{id}/share` (silent event) stays exactly as-is; reposts get the new `POST /posts/{id}/reposts` endpoint (#04).
- **Static / dynamic resource split.** Cacheable static content (`FeedPostResponse`) stays on `GET /posts/{id}`; per-viewer dynamic state (`FeedPostInteractionState`) stays on `GET /posts/{id}/interactions`. ETag/Last-Modified is added to the static endpoint only (#09).
- **Rate-limit every new write endpoint.** Each `POST` / `PATCH` / `DELETE` declares its `@RateLimited` parameters explicitly in the issue body. Read endpoints get limits when they are high-cardinality or unauthenticated.
- **Idempotency strategy declared for every non-toggle write.** Either DB-level (unique constraint with a temporal window) or `Idempotency-Key` header support. POST-toggles inherit the existing project pattern (`toggleLike`); see #04 for the repost-write strategy.
- **Surrogate IDs in path variables.** Resource identifiers in path are integer (or UUID) surrogate IDs, never user-supplied strings — keyword-mute DELETE in #06 takes `/{id}`, not `/{keyword}`.
- **Additive DTO evolution.** New fields on existing response DTOs are nullable / optional. Existing clients ignore unknown fields; new clients opt into them.
- **OpenAPI examples mandatory.** Every new endpoint carries at least one `@ExampleObject` for request (when present) and response; examples live in a dedicated `*ApiExamples.java` constants file rather than inlined annotations (#09 introduces the pattern).
- **Content negotiation honoured.** `Accept: application/ld+json` triggers the JSON-LD mapping path (#10); `Accept: application/json` returns the existing plain-JSON envelope unchanged.
- **Pagination shape unchanged at the envelope level.** `Page<T>` envelope is the source of truth for `totalElements`; `X-Total-Count` (#09) is a convenience header, not a replacement.
