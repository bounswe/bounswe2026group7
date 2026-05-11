-- Convert remaining naive TIMESTAMP columns to TIMESTAMPTZ.
-- Existing rows are interpreted as UTC, which matches the prod runtime
-- (DigitalOcean droplet JVM defaults to UTC). Notification timestamps
-- were already converted in V10.

ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE verification_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE password_reset_tokens
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE mentorship_requests
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE mentorships
    ALTER COLUMN start_date TYPE TIMESTAMPTZ USING start_date AT TIME ZONE 'UTC',
    ALTER COLUMN end_date   TYPE TIMESTAMPTZ USING end_date   AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
