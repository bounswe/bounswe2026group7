CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    first_name      VARCHAR(255) NOT NULL,
    last_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    device_token    VARCHAR(255),
    profile_photo   VARCHAR(255),
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE mentors (
    id                      BIGINT PRIMARY KEY REFERENCES users(id),
    bio                     TEXT,
    field                   VARCHAR(255),
    expertise               VARCHAR(255),
    affiliation             VARCHAR(255),
    max_mentee_capacity     INTEGER NOT NULL DEFAULT 0,
    current_mentee_count    INTEGER NOT NULL DEFAULT 0,
    preferred_mentee_major  VARCHAR(255),
    mentoring_goals         TEXT,
    mentorship_duration     INTEGER
);

CREATE TABLE mentees (
    id                  BIGINT PRIMARY KEY REFERENCES users(id),
    profile_visibility  BOOLEAN NOT NULL DEFAULT TRUE,
    goals               TEXT,
    major               VARCHAR(255),
    career_interest     VARCHAR(255),
    meeting_freq_pref   VARCHAR(255),
    background_info     TEXT,
    cancel_count        INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE mentor_interests (
    mentor_id   BIGINT NOT NULL REFERENCES mentors(id),
    interest    VARCHAR(255) NOT NULL
);

CREATE TABLE mentor_preferred_mentee_skills (
    mentor_id   BIGINT NOT NULL REFERENCES mentors(id),
    skill       VARCHAR(255) NOT NULL
);

CREATE TABLE mentee_interests (
    mentee_id   BIGINT NOT NULL REFERENCES mentees(id),
    interest    VARCHAR(255) NOT NULL
);

CREATE TABLE mentee_skills (
    mentee_id   BIGINT NOT NULL REFERENCES mentees(id),
    skill       VARCHAR(255) NOT NULL
);
