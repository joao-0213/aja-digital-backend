CREATE TABLE core.classes (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    name text NOT NULL,
    code text NOT NULL,
    normalized_code text NOT NULL UNIQUE,
    year integer NOT NULL CHECK (year BETWEEN 1900 AND 2200),
    shift text NOT NULL CHECK (shift IN ('Manhã', 'Tarde', 'Noite')),
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE core.enrollments (
    id varchar(128) PRIMARY KEY,
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    updated_by varchar(128) NOT NULL REFERENCES core.users(id),
    class_id varchar(128) NOT NULL REFERENCES core.classes(id),
    email text NOT NULL,
    normalized_email text NOT NULL,
    student_id varchar(128) REFERENCES core.users(id),
    status varchar(16) NOT NULL CHECK (status IN ('pending', 'active', 'ended', 'cancelled')),
    created_by varchar(128) NOT NULL REFERENCES core.users(id),
    enrolled_at timestamptz,
    ended_at timestamptz,
    legacy boolean NOT NULL DEFAULT false,
    unassigned_assessment_ids jsonb NOT NULL DEFAULT '[]'::jsonb
);

CREATE UNIQUE INDEX enrollments_one_open_class_email_idx
    ON core.enrollments(class_id, normalized_email) WHERE status IN ('pending', 'active');
CREATE UNIQUE INDEX enrollments_one_active_class_per_student_idx
    ON core.enrollments(student_id) WHERE status = 'active' AND student_id IS NOT NULL;
CREATE INDEX enrollments_class_status_idx ON core.enrollments(class_id, status);
CREATE INDEX enrollments_student_idx ON core.enrollments(student_id);
