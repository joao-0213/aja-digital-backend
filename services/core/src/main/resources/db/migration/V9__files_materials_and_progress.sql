CREATE TABLE core.files (
    id varchar(128) PRIMARY KEY,
    owner_id varchar(128) REFERENCES core.users(id),
    purpose varchar(16) NOT NULL CHECK (purpose IN ('material','work','forum','question')),
    parent_id varchar(128) NOT NULL,
    name varchar(255) NOT NULL,
    mime varchar(128) NOT NULL,
    size bigint NOT NULL CHECK (size >= 0),
    sha256 char(64) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('staged','attached')),
    expires_at timestamptz,
    content bytea NOT NULL,
    created_at timestamptz NOT NULL,
    CHECK ((status='staged' AND expires_at IS NOT NULL AND owner_id IS NOT NULL) OR (status='attached' AND expires_at IS NULL AND (owner_id IS NOT NULL OR purpose='question')))
);
CREATE INDEX files_staged_expiry_idx ON core.files(expires_at) WHERE status='staged';
CREATE INDEX files_owner_idx ON core.files(owner_id, purpose, parent_id, id);

-- This durable key record keeps a discarded or expired upload from being recreated by a late retry.
CREATE TABLE core.file_upload_keys (
    owner_id varchar(128) NOT NULL REFERENCES core.users(id),
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    file_id varchar(128) NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (owner_id, idempotency_key)
);

CREATE TABLE core.materials (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    author_id varchar(128) NOT NULL REFERENCES core.users(id),
    module_id varchar(128) NOT NULL REFERENCES core.modules(id),
    title varchar(160) NOT NULL CHECK (length(title) > 0),
    kind varchar(16) NOT NULL CHECK (kind IN ('lesson','pdf','video')),
    duration numeric NOT NULL CHECK (duration BETWEEN 1 AND 600),
    content text NOT NULL,
    file_id varchar(128) REFERENCES core.files(id),
    video_url text,
    order_index integer NOT NULL CHECK (order_index >= 0),
    CHECK ((kind='lesson' AND file_id IS NULL AND video_url IS NULL) OR (kind='pdf' AND file_id IS NOT NULL AND video_url IS NULL) OR (kind='video' AND file_id IS NULL AND video_url IS NOT NULL))
);
CREATE INDEX materials_module_order_idx ON core.materials(module_id, order_index, id);
CREATE UNIQUE INDEX materials_file_idx ON core.materials(file_id) WHERE file_id IS NOT NULL;

CREATE TABLE core.material_progress (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    material_id varchar(128) NOT NULL REFERENCES core.materials(id),
    completed boolean NOT NULL DEFAULT false,
    last_access timestamptz NOT NULL,
    UNIQUE (user_id, material_id)
);
CREATE INDEX material_progress_user_idx ON core.material_progress(user_id, id);

ALTER TABLE core.audit_events DROP CONSTRAINT audit_events_event_type_v8_check;
ALTER TABLE core.audit_events ADD CONSTRAINT audit_events_event_type_v9_check CHECK (event_type IN (
    'auth.registration','auth.login','auth.logout','auth.email_confirmation_request','auth.password_reset_request','auth.email_confirmation','auth.password_reset','auth.profile_update',
    'academic.user_created','academic.user_updated','academic.user_role_changed','academic.user_deactivated','academic.user_withdrawn',
    'academic.class_created','academic.class_updated','academic.class_closed','academic.class_reopened','academic.enrollment_created','academic.enrollment_activated','academic.enrollment_cancelled','academic.enrollment_ended',
    'academic.subject_created','academic.subject_updated','academic.module_created','academic.module_updated','academic.modules_reordered',
    'academic.materials_published','academic.material_updated','academic.materials_reordered'
)) NOT VALID;
ALTER TABLE core.audit_events VALIDATE CONSTRAINT audit_events_event_type_v9_check;
