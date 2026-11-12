CREATE TABLE core.announcements (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    created_by varchar(128) NOT NULL REFERENCES core.users(id),
    class_id varchar(128) NOT NULL REFERENCES core.classes(id),
    subject_id varchar(128) REFERENCES core.subjects(id),
    title varchar(160) NOT NULL CHECK (length(btrim(title)) > 0),
    body text NOT NULL CHECK (length(body) <= 10000),
    notify boolean NOT NULL,
    occurrence jsonb CHECK (occurrence IS NULL OR jsonb_typeof(occurrence) = 'object'),
    withdrawn_at timestamptz
);

ALTER TABLE core.subjects ADD CONSTRAINT subjects_id_class_unique UNIQUE (id, class_id);
ALTER TABLE core.announcements ADD CONSTRAINT announcements_subject_class_fk FOREIGN KEY (subject_id, class_id) REFERENCES core.subjects(id, class_id) ON UPDATE CASCADE;

CREATE INDEX announcements_class_created_idx ON core.announcements(class_id, created_at, id) WHERE withdrawn_at IS NULL;
CREATE INDEX announcements_subject_created_idx ON core.announcements(subject_id, created_at, id) WHERE withdrawn_at IS NULL;

CREATE TABLE core.announcement_notification_recipients (
    announcement_id varchar(128) NOT NULL REFERENCES core.announcements(id),
    revision integer NOT NULL CHECK (revision > 0),
    recipient_id varchar(128) NOT NULL REFERENCES core.users(id),
    event_id varchar(128) NOT NULL UNIQUE,
    PRIMARY KEY (announcement_id, revision, recipient_id)
);

ALTER TABLE core.audit_events DROP CONSTRAINT audit_events_event_type_v13_check;
ALTER TABLE core.audit_events ADD CONSTRAINT audit_events_event_type_v14_check CHECK (event_type IN (
    'auth.registration','auth.login','auth.logout','auth.email_confirmation_request','auth.password_reset_request','auth.email_confirmation','auth.password_reset','auth.profile_update',
    'academic.user_created','academic.user_updated','academic.user_role_changed','academic.user_deactivated','academic.user_withdrawn',
    'academic.class_created','academic.class_updated','academic.class_closed','academic.class_reopened','academic.enrollment_created','academic.enrollment_activated','academic.enrollment_cancelled','academic.enrollment_ended',
    'academic.subject_created','academic.subject_updated','academic.module_created','academic.module_updated','academic.modules_reordered',
    'academic.materials_published','academic.material_updated','academic.materials_reordered',
    'academic.assessment_created','academic.assessment_updated','academic.assessment_published',
    'academic.submission_created','academic.submission_updated','academic.submission_submitted','academic.feedback_saved','academic.feedback_published',
    'academic.announcement_created','academic.announcement_updated','academic.announcement_withdrawn'
)) NOT VALID;
ALTER TABLE core.audit_events VALIDATE CONSTRAINT audit_events_event_type_v14_check;
