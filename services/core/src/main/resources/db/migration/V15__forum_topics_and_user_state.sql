CREATE TABLE core.topics (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    last_activity_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    author_id varchar(128) NOT NULL REFERENCES core.users(id),
    class_id varchar(128) NOT NULL REFERENCES core.classes(id),
    subject_id varchar(128) NOT NULL,
    title varchar(160) NOT NULL CHECK (length(btrim(title)) > 0),
    body text NOT NULL CHECK (length(body) BETWEEN 1 AND 10000),
    tags jsonb NOT NULL CHECK (jsonb_typeof(tags)='array'),
    target_type varchar(16) CHECK (target_type IN ('question','material')),
    target_id varchar(128),
    attachment_id varchar(128) REFERENCES core.files(id),
    solution_reply_id varchar(128),
    withdrawn_at timestamptz,
    FOREIGN KEY (subject_id,class_id) REFERENCES core.subjects(id,class_id),
    CHECK ((target_type IS NULL)=(target_id IS NULL))
);
CREATE INDEX topics_scope_created_idx ON core.topics(class_id,subject_id,created_at,id) WHERE withdrawn_at IS NULL;
CREATE INDEX topics_attachment_idx ON core.topics(attachment_id) WHERE attachment_id IS NOT NULL;
CREATE FUNCTION core.fold_forum(value text) RETURNS text LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT regexp_replace(normalize(lower(value), NFD), U&'[\0300-\036f]', '', 'g')
$$;
CREATE TABLE core.topic_replies (
    id varchar(128) PRIMARY KEY,
    topic_id varchar(128) NOT NULL REFERENCES core.topics(id),
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    author_id varchar(128) NOT NULL REFERENCES core.users(id),
    body text NOT NULL CHECK (length(body) BETWEEN 1 AND 10000),
    withdrawn_at timestamptz
);
CREATE INDEX topic_replies_topic_created_idx ON core.topic_replies(topic_id,created_at,id) WHERE withdrawn_at IS NULL;
CREATE TABLE core.topic_reads (
    topic_id varchar(128) NOT NULL REFERENCES core.topics(id),
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    read_at timestamptz NOT NULL,
    PRIMARY KEY (topic_id,user_id)
);
CREATE TABLE core.topic_votes (
    reply_id varchar(128) NOT NULL REFERENCES core.topic_replies(id),
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (reply_id,user_id)
);
ALTER TABLE core.audit_events DROP CONSTRAINT audit_events_event_type_v14_check;
ALTER TABLE core.audit_events ADD CONSTRAINT audit_events_event_type_v15_check CHECK (event_type IN (
    'auth.registration','auth.login','auth.logout','auth.email_confirmation_request','auth.password_reset_request','auth.email_confirmation','auth.password_reset','auth.profile_update',
    'academic.user_created','academic.user_updated','academic.user_role_changed','academic.user_deactivated','academic.user_withdrawn',
    'academic.class_created','academic.class_updated','academic.class_closed','academic.class_reopened','academic.enrollment_created','academic.enrollment_activated','academic.enrollment_cancelled','academic.enrollment_ended',
    'academic.subject_created','academic.subject_updated','academic.module_created','academic.module_updated','academic.modules_reordered',
    'academic.materials_published','academic.material_updated','academic.materials_reordered',
    'academic.assessment_created','academic.assessment_updated','academic.assessment_published',
    'academic.submission_created','academic.submission_updated','academic.submission_submitted','academic.feedback_saved','academic.feedback_published',
    'academic.announcement_created','academic.announcement_updated','academic.announcement_withdrawn',
    'academic.topic_created','academic.topic_withdrawn','academic.topic_solution_changed','academic.topic_reply_created','academic.topic_reply_withdrawn','academic.topic_read','academic.topic_vote'
)) NOT VALID;
ALTER TABLE core.audit_events VALIDATE CONSTRAINT audit_events_event_type_v15_check;
