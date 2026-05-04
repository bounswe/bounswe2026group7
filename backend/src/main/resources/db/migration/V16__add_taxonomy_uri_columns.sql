-- Add controlled-vocabulary identifier columns to profile taxonomy fields.
-- See issue #320 for context.
--
-- Three vocabularies, all using http:// linked-data identifiers:
--   ESCO       http://data.europa.eu/esco/skill/<uuid>
--   ISCED-F    http://data.europa.eu/esco/isced-f/<4-digit>   (via ESCO)
--   Wikidata   http://www.wikidata.org/entity/Q<id>
--
-- All new columns are nullable; existing rows continue to load with null
-- identifier columns. No existing column is altered or dropped.

-- Scalar URI columns on mentors and mentees ---------------------------------

ALTER TABLE mentors
    ADD COLUMN expertise_uri              VARCHAR(255),
    ADD COLUMN field_uri                  VARCHAR(255),
    ADD COLUMN preferred_mentee_major_uri VARCHAR(255);

ALTER TABLE mentees
    ADD COLUMN career_interest_uri VARCHAR(255),
    ADD COLUMN major_uri           VARCHAR(255);

-- URI columns on element-collection tables ---------------------------------

ALTER TABLE mentor_interests
    ADD COLUMN identifier_uri VARCHAR(255);

ALTER TABLE mentor_preferred_mentee_skills
    ADD COLUMN identifier_uri VARCHAR(255);

ALTER TABLE mentee_interests
    ADD COLUMN identifier_uri VARCHAR(255);

ALTER TABLE mentee_skills
    ADD COLUMN identifier_uri VARCHAR(255);

-- Partial indexes on URI columns. The recommendation and search paths join
-- on URI when present, so the column is on the read path. Partial indexes
-- (WHERE column IS NOT NULL) keep the index small while legacy free-text
-- profiles dominate the table.

CREATE INDEX idx_mentors_expertise_uri
    ON mentors (expertise_uri)
    WHERE expertise_uri IS NOT NULL;

CREATE INDEX idx_mentors_field_uri
    ON mentors (field_uri)
    WHERE field_uri IS NOT NULL;

CREATE INDEX idx_mentors_preferred_mentee_major_uri
    ON mentors (preferred_mentee_major_uri)
    WHERE preferred_mentee_major_uri IS NOT NULL;

CREATE INDEX idx_mentees_career_interest_uri
    ON mentees (career_interest_uri)
    WHERE career_interest_uri IS NOT NULL;

CREATE INDEX idx_mentees_major_uri
    ON mentees (major_uri)
    WHERE major_uri IS NOT NULL;

CREATE INDEX idx_mentor_interests_identifier_uri
    ON mentor_interests (identifier_uri)
    WHERE identifier_uri IS NOT NULL;

CREATE INDEX idx_mentor_preferred_mentee_skills_identifier_uri
    ON mentor_preferred_mentee_skills (identifier_uri)
    WHERE identifier_uri IS NOT NULL;

CREATE INDEX idx_mentee_interests_identifier_uri
    ON mentee_interests (identifier_uri)
    WHERE identifier_uri IS NOT NULL;

CREATE INDEX idx_mentee_skills_identifier_uri
    ON mentee_skills (identifier_uri)
    WHERE identifier_uri IS NOT NULL;
