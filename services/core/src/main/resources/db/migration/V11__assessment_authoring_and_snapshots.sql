ALTER TABLE core.files DROP CONSTRAINT files_purpose_check;
ALTER TABLE core.files ADD CONSTRAINT files_purpose_check CHECK (purpose IN ('material','work','forum','question','assessment'));
ALTER TABLE core.files DROP CONSTRAINT files_check;
ALTER TABLE core.files ADD CONSTRAINT files_state_check CHECK (
    (status='staged' AND expires_at IS NOT NULL AND owner_id IS NOT NULL)
    OR (status='attached' AND expires_at IS NULL AND (owner_id IS NOT NULL OR purpose IN ('question','assessment')))
);

ALTER TABLE core.assessments
    ADD COLUMN created_at timestamptz,
    ADD COLUMN updated_at timestamptz,
    ADD COLUMN updated_by varchar(128),
    ADD COLUMN title varchar(160),
    ADD COLUMN description text,
    ADD COLUMN max_grade numeric,
    ADD COLUMN required boolean NOT NULL DEFAULT true,
    ADD COLUMN order_index integer NOT NULL DEFAULT 0,
    ADD COLUMN questions_updated_at timestamptz;

UPDATE core.assessments a
SET created_at = COALESCE(a.published_at, now()),
    updated_at = COALESCE(a.published_at, now()),
    updated_by = m.updated_by,
    title = 'Imported assessment',
    description = ''
FROM core.modules m
WHERE m.id = a.module_id;

WITH ordered AS (
    SELECT id, row_number() OVER (PARTITION BY module_id ORDER BY id) - 1 AS position
    FROM core.assessments
)
UPDATE core.assessments a SET order_index = ordered.position FROM ordered WHERE a.id = ordered.id;

ALTER TABLE core.assessments
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN description SET NOT NULL,
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET DEFAULT now(),
    ADD CONSTRAINT assessments_updated_by_fk FOREIGN KEY (updated_by) REFERENCES core.users(id),
    ADD CONSTRAINT assessments_title_check CHECK (length(btrim(title)) > 0 AND length(title) <= 160),
    ADD CONSTRAINT assessments_description_check CHECK (length(description) <= 10000),
    ADD CONSTRAINT assessments_max_grade_check CHECK (max_grade IS NULL OR max_grade > 0),
    ADD CONSTRAINT assessments_order_index_check CHECK (order_index >= 0);

CREATE INDEX assessments_module_order_idx ON core.assessments(module_id, order_index, id);

CREATE TABLE core.assessment_questions (
    assessment_id varchar(128) NOT NULL REFERENCES core.assessments(id) ON DELETE CASCADE,
    question_id varchar(128) NOT NULL,
    position integer NOT NULL CHECK (position >= 0),
    source varchar(16) NOT NULL CHECK (source IN ('bank','author')),
    source_revision integer,
    content_hash char(64),
    snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'),
    PRIMARY KEY (assessment_id, question_id),
    UNIQUE (assessment_id, position)
);

ALTER TABLE core.audit_events DROP CONSTRAINT audit_events_event_type_v9_check;
ALTER TABLE core.audit_events ADD CONSTRAINT audit_events_event_type_v11_check CHECK (event_type IN (
    'auth.registration','auth.login','auth.logout','auth.email_confirmation_request','auth.password_reset_request','auth.email_confirmation','auth.password_reset','auth.profile_update',
    'academic.user_created','academic.user_updated','academic.user_role_changed','academic.user_deactivated','academic.user_withdrawn',
    'academic.class_created','academic.class_updated','academic.class_closed','academic.class_reopened','academic.enrollment_created','academic.enrollment_activated','academic.enrollment_cancelled','academic.enrollment_ended',
    'academic.subject_created','academic.subject_updated','academic.module_created','academic.module_updated','academic.modules_reordered',
    'academic.materials_published','academic.material_updated','academic.materials_reordered',
    'academic.assessment_created','academic.assessment_updated','academic.assessment_published'
)) NOT VALID;
ALTER TABLE core.audit_events VALIDATE CONSTRAINT audit_events_event_type_v11_check;
