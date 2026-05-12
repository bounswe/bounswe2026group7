-- Mentees gain an optional `affiliation` column mirroring the existing one
-- on `mentors`. The public profile read (`GET /api/users/{id}`) returns the
-- column for both roles so the web UI can render an "Affiliation" field
-- without role-aware branching. Existing mentee rows keep NULL on backfill.
ALTER TABLE mentees
    ADD COLUMN affiliation VARCHAR(255);
