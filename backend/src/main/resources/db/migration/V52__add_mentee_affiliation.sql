-- Mirrors the mentor-side `affiliation` column on `mentees` so the mentee
-- profile can also surface a university / company affiliation. Mentor
-- carries this in V1 (`mentors.affiliation VARCHAR(255)`); the mentee
-- table was created without it, leaving the API unable to round-trip the
-- value even though the profile UI has a slot for it.
--
-- VARCHAR(255) matches the mentor column for symmetry. Application-level
-- validation in `MenteeProfileRequest` caps user input at 200 characters
-- (matching `MentorProfileRequest.affiliation`); the DB ceiling is the
-- defence-in-depth backstop.
--
-- Nullable: existing rows have no affiliation. Future rows may omit it.

ALTER TABLE mentees
    ADD COLUMN affiliation VARCHAR(255);
