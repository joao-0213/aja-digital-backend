CREATE TABLE email.delivery_events (
    event_id varchar(128) PRIMARY KEY,
    event_type varchar(128) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('pending', 'sending', 'retry', 'sent', 'unknown', 'dead')),
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    last_error varchar(80)
);

CREATE INDEX delivery_events_status_idx ON email.delivery_events(status, updated_at);
