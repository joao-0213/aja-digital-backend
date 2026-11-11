CREATE TABLE core.users (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL,
    name text NOT NULL,
    email text NOT NULL,
    normalized_email text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('student', 'teacher', 'admin')),
    active boolean NOT NULL,
    email_confirmed_at timestamptz
);

CREATE TABLE core.sessions (
    id varchar(128) PRIMARY KEY,
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    token_hash char(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz
);

CREATE TABLE core.auth_tokens (
    id varchar(128) PRIMARY KEY,
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    purpose varchar(32) NOT NULL CHECK (purpose IN ('email_confirmation', 'password_reset')),
    token_hash char(64) NOT NULL UNIQUE,
    email_at_issue text NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz
);

CREATE INDEX auth_tokens_user_purpose_idx ON core.auth_tokens(user_id, purpose);
CREATE INDEX sessions_user_idx ON core.sessions(user_id);

CREATE TABLE core.outbox_events (
    id varchar(128) PRIMARY KEY,
    event_type varchar(128) NOT NULL,
    event_version integer NOT NULL,
    created_at timestamptz NOT NULL,
    key_id varchar(128) NOT NULL,
    nonce bytea NOT NULL,
    encrypted_payload bytea NOT NULL,
    published_at timestamptz
);
