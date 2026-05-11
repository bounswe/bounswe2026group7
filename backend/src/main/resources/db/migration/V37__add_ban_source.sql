-- Adds a discriminator column to bans so each row knows which path
-- imposed it (#345 review feedback).
--
-- Why this exists: the original schema (V30) carried only `reason` as a
-- free-form string to distinguish ban kinds. The admin clear-bot-flag
-- flow (#345) used `getActiveBan(userId)` to find a ban to lift, but
-- that query returns the user's latest-expiring active ban regardless
-- of origin — so clearing a bot flag could lift an unrelated admin ban
-- if the admin ban happened to expire later than the system spam ban.
--
-- Fix: persist the origin explicitly. New code filters by source when
-- the lift policy is path-specific (system spam → SYSTEM_SPAM only);
-- the generic admin "unban user" path still treats any active row.
--
-- Backfill heuristic: pre-#345 rows are either mentee cancellations
-- (reason set by recordCancellation in BanService — typically containing
-- "cancellation" / "cancel"; production also uses
-- "Frequent cancellations") or admin bans (#280, any other reason).
-- The discriminator is for forward-looking lift policies, so the only
-- value that matters here is that no historical row gets stamped
-- SYSTEM_SPAM; a small bucket of MENTEE_CANCELLATION rows mis-classified
-- as ADMIN (or vice versa) cannot misroute a clear-flag click.
--
-- Rollback: ALTER TABLE bans DROP COLUMN source.

ALTER TABLE bans
    ADD COLUMN source VARCHAR(32);

UPDATE bans
SET source = CASE
    WHEN LOWER(reason) LIKE '%cancellation%' OR LOWER(reason) LIKE '%cancel%'
        THEN 'MENTEE_CANCELLATION'
    ELSE 'ADMIN'
END
WHERE source IS NULL;

ALTER TABLE bans
    ALTER COLUMN source SET NOT NULL;

-- No index on source by itself — neither the active-ban lookup nor the
-- expiry scheduler filter on it. The (small) findActiveBySource query
-- piggybacks on the existing partial index over (user_id, expires_at)
-- and applies source as a residual predicate.
