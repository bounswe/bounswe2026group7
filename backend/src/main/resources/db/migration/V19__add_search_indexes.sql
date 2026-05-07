-- DB-level search indexes for the matching/search overhaul (#262).
--
-- Two index families:
--   1. GIN trigram (pg_trgm) for accelerated keyword search across the mentor
--      and mentee text fields plus their element-collection labels (interests,
--      skills). pg_trgm requires ≥3 alphanumerics in the pattern; shorter
--      keywords silently seq-scan otherwise — the service skips the keyword
--      filter for q.length() < 3 to avoid this.
--
--      All trigram indexes are FUNCTIONAL on LOWER(col), not the raw column.
--      The JPQL queries lowercase the column for case-insensitive matching:
--      `WHERE LOWER(m.expertise) LIKE :keyword`. Postgres only uses an index
--      when the indexed expression matches the predicate's left side
--      textually, so an index on `expertise` is never used for
--      `LOWER(expertise) LIKE`. The functional shape `gin (LOWER(expertise)
--      gin_trgm_ops)` lets the planner choose a Bitmap Index Scan.
--      (Empirically verified with EXPLAIN ANALYZE before this fix; the raw-
--      column form fell back to seq scan even with enable_seqscan=off.)
--
--   2. Btree composite on (entity_id, day_of_week) for the hasAvailability
--      EXISTS subquery that joins mentor + mentee availability tables.
--      Without these the day-of-week predicate falls back to seq scan.
--
-- pg_trgm is supported on DigitalOcean Managed PostgreSQL and the docker-
-- compose Postgres user 'group7' has CREATE on the database. New deploy
-- environments must grant CREATE on the database before this migration
-- runs for the first time.
--
-- Rollback: drop indexes individually + DROP EXTENSION IF EXISTS pg_trgm
-- CASCADE. No schema dependencies on the extension itself today.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Mentor scalar columns
CREATE INDEX idx_mentors_expertise_trgm
    ON mentors USING gin (LOWER(expertise) gin_trgm_ops);
CREATE INDEX idx_mentors_field_trgm
    ON mentors USING gin (LOWER(field) gin_trgm_ops);
CREATE INDEX idx_mentors_mentoring_goals_trgm
    ON mentors USING gin (LOWER(mentoring_goals) gin_trgm_ops);

-- Mentor element-collection tables — column name is `interest` / `skill`.
-- TaggedTerm.label is mapped here via @AttributeOverride; the index targets
-- the column, not the entity field name.
CREATE INDEX idx_mentor_interests_trgm
    ON mentor_interests USING gin (LOWER(interest) gin_trgm_ops);
CREATE INDEX idx_mentor_preferred_skills_trgm
    ON mentor_preferred_mentee_skills USING gin (LOWER(skill) gin_trgm_ops);

-- Mentee scalar columns
CREATE INDEX idx_mentees_goals_trgm
    ON mentees USING gin (LOWER(goals) gin_trgm_ops);
CREATE INDEX idx_mentees_major_trgm
    ON mentees USING gin (LOWER(major) gin_trgm_ops);
CREATE INDEX idx_mentees_career_interest_trgm
    ON mentees USING gin (LOWER(career_interest) gin_trgm_ops);
CREATE INDEX idx_mentees_background_info_trgm
    ON mentees USING gin (LOWER(background_info) gin_trgm_ops);

-- Mentee element-collection tables
CREATE INDEX idx_mentee_interests_trgm
    ON mentee_interests USING gin (LOWER(interest) gin_trgm_ops);
CREATE INDEX idx_mentee_skills_trgm
    ON mentee_skills USING gin (LOWER(skill) gin_trgm_ops);

-- Btree composite indexes for hasAvailability overlap subquery.
-- day_of_week is stored as VARCHAR via @Enumerated(EnumType.STRING); a
-- regular btree on the composite is the right shape — do not reach for hash.
CREATE INDEX idx_mentor_avail_mentor_day
    ON mentor_availability_slots (mentor_id, day_of_week);
CREATE INDEX idx_mentee_avail_mentee_day
    ON mentee_availability_slots (mentee_id, day_of_week);
