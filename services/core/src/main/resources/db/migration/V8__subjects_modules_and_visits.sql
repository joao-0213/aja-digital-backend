CREATE TABLE core.subjects (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    class_id varchar(128) NOT NULL REFERENCES core.classes(id),
    name varchar(100) NOT NULL CHECK (length(name) > 0),
    color varchar(16) NOT NULL CHECK (color IN ('violet','blue','amber','green')),
    icon varchar(24) NOT NULL CHECK (icon IN ('book','calculator','atom','flask','leaf','globe','languages','landmark','palette','graduation')),
    description text NOT NULL CHECK (length(description) <= 10000)
);
CREATE INDEX subjects_class_id_idx ON core.subjects(class_id, id);

CREATE TABLE core.subject_teachers (
    subject_id varchar(128) NOT NULL REFERENCES core.subjects(id) ON DELETE CASCADE,
    teacher_id varchar(128) NOT NULL REFERENCES core.users(id),
    PRIMARY KEY (subject_id, teacher_id)
);
CREATE INDEX subject_teachers_teacher_idx ON core.subject_teachers(teacher_id, subject_id);

CREATE TABLE core.modules (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    subject_id varchar(128) NOT NULL REFERENCES core.subjects(id),
    title varchar(160) NOT NULL CHECK (length(title) > 0),
    description text NOT NULL CHECK (length(description) <= 10000),
    order_index integer NOT NULL CHECK (order_index >= 0),
    withdrawn_at timestamptz
);
CREATE INDEX modules_subject_order_idx ON core.modules(subject_id, order_index, id) WHERE withdrawn_at IS NULL;

CREATE TABLE core.subject_publications (
    subject_id varchar(128) NOT NULL REFERENCES core.subjects(id) ON DELETE CASCADE,
    publication_id varchar(139) NOT NULL CHECK (publication_id ~ '^(material|notice|assessment):[A-Za-z0-9][A-Za-z0-9._:-]*$'),
    PRIMARY KEY (subject_id, publication_id)
);
COMMENT ON TABLE core.subject_publications IS 'Core projection of visible publication IDs; publication content remains owned by its Core domain.';

CREATE TABLE core.subject_visits (
    subject_id varchar(128) NOT NULL REFERENCES core.subjects(id) ON DELETE CASCADE,
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    seen_at timestamptz,
    publication_ids jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(publication_ids)='array'),
    PRIMARY KEY (subject_id, user_id)
);

ALTER TABLE core.audit_events ADD CONSTRAINT audit_events_event_type_v8_check CHECK (event_type IN (
    'auth.registration','auth.login','auth.logout','auth.email_confirmation_request','auth.password_reset_request','auth.email_confirmation','auth.password_reset','auth.profile_update',
    'academic.user_created','academic.user_updated','academic.user_role_changed','academic.user_deactivated','academic.user_withdrawn',
    'academic.class_created','academic.class_updated','academic.class_closed','academic.class_reopened','academic.enrollment_created','academic.enrollment_activated','academic.enrollment_cancelled','academic.enrollment_ended',
    'academic.subject_created','academic.subject_updated','academic.module_created','academic.module_updated','academic.modules_reordered'
)) NOT VALID;
ALTER TABLE core.audit_events DROP CONSTRAINT audit_events_event_type_check;
ALTER TABLE core.audit_events VALIDATE CONSTRAINT audit_events_event_type_v8_check;
