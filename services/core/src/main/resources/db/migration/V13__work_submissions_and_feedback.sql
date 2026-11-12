CREATE TABLE core.submissions (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    assessment_id varchar(128) NOT NULL REFERENCES core.assessments(id),
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    attachments jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(attachments)='array' AND jsonb_array_length(attachments)<=5),
    submitted_at timestamptz,
    receipt_id varchar(128) UNIQUE,
    feedback_draft jsonb CHECK (feedback_draft IS NULL OR jsonb_typeof(feedback_draft)='object'),
    feedback_published jsonb CHECK (feedback_published IS NULL OR jsonb_typeof(feedback_published)='object'),
    feedback_updated boolean NOT NULL DEFAULT false,
    feedback_history jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(feedback_history)='array'),
    UNIQUE (assessment_id,user_id),
    CHECK ((submitted_at IS NULL AND receipt_id IS NULL) OR (submitted_at IS NOT NULL AND receipt_id IS NOT NULL))
);

CREATE INDEX submissions_user_updated_idx ON core.submissions(user_id,updated_at DESC,id);
CREATE INDEX submissions_assessment_submitted_idx ON core.submissions(assessment_id,submitted_at,id);

ALTER TABLE core.audit_events DROP CONSTRAINT audit_events_event_type_v11_check;
ALTER TABLE core.audit_events ADD CONSTRAINT audit_events_event_type_v13_check CHECK (event_type IN (
    'auth.registration','auth.login','auth.logout','auth.email_confirmation_request','auth.password_reset_request','auth.email_confirmation','auth.password_reset','auth.profile_update',
    'academic.user_created','academic.user_updated','academic.user_role_changed','academic.user_deactivated','academic.user_withdrawn',
    'academic.class_created','academic.class_updated','academic.class_closed','academic.class_reopened','academic.enrollment_created','academic.enrollment_activated','academic.enrollment_cancelled','academic.enrollment_ended',
    'academic.subject_created','academic.subject_updated','academic.module_created','academic.module_updated','academic.modules_reordered',
    'academic.materials_published','academic.material_updated','academic.materials_reordered',
    'academic.assessment_created','academic.assessment_updated','academic.assessment_published',
    'academic.submission_created','academic.submission_updated','academic.submission_submitted','academic.feedback_saved','academic.feedback_published'
)) NOT VALID;
ALTER TABLE core.audit_events VALIDATE CONSTRAINT audit_events_event_type_v13_check;
