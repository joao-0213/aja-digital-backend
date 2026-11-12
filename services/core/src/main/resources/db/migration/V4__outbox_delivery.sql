ALTER TABLE core.outbox_events
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    ADD COLUMN next_attempt_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN last_error varchar(80),
    ADD COLUMN failed_at timestamptz;

CREATE INDEX outbox_events_pending_idx
    ON core.outbox_events(next_attempt_at, created_at)
    WHERE published_at IS NULL AND failed_at IS NULL;
