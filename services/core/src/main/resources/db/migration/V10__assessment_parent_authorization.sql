CREATE TABLE core.assessments (
    id varchar(128) PRIMARY KEY,
    module_id varchar(128) NOT NULL REFERENCES core.modules(id),
    kind varchar(16) NOT NULL CHECK (kind IN ('quiz','work')),
    status varchar(16) NOT NULL CHECK (status IN ('draft','published')),
    revision integer NOT NULL CHECK (revision > 0),
    published_at timestamptz,
    deadline timestamptz,
    allow_late boolean NOT NULL DEFAULT false,
    CHECK ((status='draft' AND published_at IS NULL) OR (status='published' AND published_at IS NOT NULL))
);

CREATE INDEX assessments_module_status_kind_idx ON core.assessments(module_id,status,kind,id);
