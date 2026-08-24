-- Core pilot/auth schema, written fresh as one consolidated migration rather than the incremental
-- history a schema like this typically accumulates over time - this is a new repo, so there's no
-- prior deployed shape to stay compatible with yet.

CREATE TABLE pilot (
    id          UUID          PRIMARY KEY,
    name        VARCHAR(255)  NOT NULL,
    email       VARCHAR(255),
    disabled_at TIMESTAMP WITH TIME ZONE,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pilot_email_unique UNIQUE (email)
);

CREATE TABLE auth_identity (
    id                UUID          NOT NULL PRIMARY KEY,
    pilot_id          UUID          NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    identifier        VARCHAR(255)  NOT NULL,
    hashed_credential VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_auth_identity_pilot FOREIGN KEY (pilot_id) REFERENCES pilot(id),
    CONSTRAINT uq_auth_identity_type_identifier UNIQUE (type, identifier)
);

CREATE TABLE admin (
    pilot_id UUID PRIMARY KEY REFERENCES pilot(id)
);

CREATE TABLE referral_code (
    code           VARCHAR PRIMARY KEY,
    created_by     UUID NOT NULL REFERENCES pilot(id),
    used_by        UUID REFERENCES pilot(id),
    used_at        TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    invited_email  VARCHAR NOT NULL,
    expires_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at   TIMESTAMP WITH TIME ZONE
);

CREATE TABLE password_reset_code (
    id          UUID NOT NULL PRIMARY KEY,
    pilot_id    UUID NOT NULL REFERENCES pilot(id),
    code        VARCHAR(6) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_password_reset_code_pilot_id ON password_reset_code(pilot_id);

CREATE TABLE session (
    id                UUID NOT NULL PRIMARY KEY,
    pilot_id          UUID NOT NULL REFERENCES pilot(id),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_accessed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_session_pilot_id ON session(pilot_id);
CREATE INDEX idx_session_last_accessed_at ON session(last_accessed_at);

CREATE TABLE rate_limit_bucket (
    client_ip      VARCHAR NOT NULL PRIMARY KEY,
    window_start   TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count  INT NOT NULL
);

CREATE TABLE failed_attempt (
    attempt_key    VARCHAR NOT NULL,
    purpose        VARCHAR NOT NULL,
    window_start   TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count  INT NOT NULL,
    PRIMARY KEY (attempt_key, purpose)
);
