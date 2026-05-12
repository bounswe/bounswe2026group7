-- V54: add mentors.profile_visibility (issue #570).
--
-- Mirrors mentees.profile_visibility (V1). NOT NULL DEFAULT TRUE keeps every
-- existing mentor publicly visible after rollout — the column is added in
-- "public-by-default" state so behaviour matches the pre-#570 wire contract
-- until a mentor explicitly toggles the field via PATCH /api/users/me/mentor.
--
-- Boolean (not enum) for this PR. Future work may promote this column to a
-- 3-level enum (AUTHENTICATED_ONLY / PRIVATE / MENTOR_PAIR_ONLY); the boolean
-- shape is forward-compatible — true → AUTHENTICATED_ONLY, false → PRIVATE.

ALTER TABLE mentors ADD COLUMN profile_visibility BOOLEAN NOT NULL DEFAULT TRUE;
