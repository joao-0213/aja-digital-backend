CREATE TABLE question_bank (
    id text PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'),
    revision integer NOT NULL CHECK (revision > 0),
    created_at timestamptz NOT NULL,
    subject text NOT NULL,
    topic text NOT NULL,
    difficulty text NOT NULL CHECK (difficulty IN ('easy', 'medium', 'hard')),
    institution text NOT NULL,
    year integer NOT NULL CHECK (year >= 0),
    statement text NOT NULL,
    options text[] NOT NULL CHECK (cardinality(options) >= 2),
    correct_option integer NOT NULL CHECK (correct_option >= 0 AND correct_option < cardinality(options)),
    explanation text NOT NULL,
    image_file_id text,
    image_alt text,
    withdrawn_at timestamptz,
    CHECK ((image_file_id IS NULL) = (image_alt IS NULL)),
    CHECK (image_file_id IS NULL OR (btrim(image_file_id) <> '' AND btrim(image_alt) <> '')),
    CHECK (array_position(options, NULL) IS NULL)
);

CREATE INDEX question_bank_created_id_idx ON question_bank (created_at, id) WHERE withdrawn_at IS NULL;
