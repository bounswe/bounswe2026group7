-- #335: Add shared_goal column to mentorships table.
-- Nullable TEXT so existing rows are unaffected (NULL = no goal set yet).
-- The shared-goal precondition gate checks this column before allowing
-- task / milestone / meeting writes on a mentorship.
ALTER TABLE mentorships
    ADD COLUMN IF NOT EXISTS shared_goal TEXT;
