CREATE TABLE core.attempts (
    id varchar(128) PRIMARY KEY,
    assessment_id varchar(128) NOT NULL REFERENCES core.assessments(id),
    user_id varchar(128) NOT NULL REFERENCES core.users(id),
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    started_at timestamptz NOT NULL,
    submitted_at timestamptz,
    answers jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(answers) = 'object'),
    question_order jsonb NOT NULL CHECK (jsonb_typeof(question_order) = 'array'),
    questions jsonb NOT NULL CHECK (jsonb_typeof(questions) = 'array'),
    questions_updated_at timestamptz,
    receipt_id varchar(128) UNIQUE,
    score numeric,
    max_score numeric,
    UNIQUE (assessment_id, user_id),
    CHECK ((submitted_at IS NULL AND receipt_id IS NULL AND score IS NULL AND max_score IS NULL)
        OR (submitted_at IS NOT NULL AND receipt_id IS NOT NULL AND score IS NOT NULL AND max_score IS NOT NULL))
);

CREATE INDEX attempts_user_updated_idx ON core.attempts(user_id, updated_at DESC, id);
CREATE INDEX attempts_assessment_submitted_idx ON core.attempts(assessment_id, submitted_at, id);
