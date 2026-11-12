CREATE TABLE core.attendance (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    subject_id varchar(128) NOT NULL REFERENCES core.subjects(id),
    class_date date NOT NULL,
    class_time time NOT NULL,
    records jsonb NOT NULL CHECK (jsonb_typeof(records) = 'object'),
    UNIQUE (subject_id, class_date, class_time)
);
CREATE INDEX attendance_subject_date_idx ON core.attendance(subject_id, class_date DESC, id);

ALTER TABLE core.audit_events DROP CONSTRAINT audit_events_event_type_v15_check;
ALTER TABLE core.audit_events ADD CONSTRAINT audit_events_event_type_v16_check CHECK (event_type IN (
    'auth.registration','auth.login','auth.logout','auth.email_confirmation_request','auth.password_reset_request','auth.email_confirmation','auth.password_reset','auth.profile_update',
    'academic.user_created','academic.user_updated','academic.user_role_changed','academic.user_deactivated','academic.user_withdrawn',
    'academic.class_created','academic.class_updated','academic.class_closed','academic.class_reopened','academic.enrollment_created','academic.enrollment_activated','academic.enrollment_cancelled','academic.enrollment_ended',
    'academic.subject_created','academic.subject_updated','academic.module_created','academic.module_updated','academic.modules_reordered',
    'academic.materials_published','academic.material_updated','academic.materials_reordered',
    'academic.assessment_created','academic.assessment_updated','academic.assessment_published',
    'academic.submission_created','academic.submission_updated','academic.submission_submitted','academic.feedback_saved','academic.feedback_published',
    'academic.announcement_created','academic.announcement_updated','academic.announcement_withdrawn',
    'academic.topic_created','academic.topic_withdrawn','academic.topic_solution_changed','academic.topic_reply_created','academic.topic_reply_withdrawn','academic.topic_read','academic.topic_vote',
    'academic.attendance_created','academic.attendance_updated'
)) NOT VALID;
ALTER TABLE core.audit_events VALIDATE CONSTRAINT audit_events_event_type_v16_check;
