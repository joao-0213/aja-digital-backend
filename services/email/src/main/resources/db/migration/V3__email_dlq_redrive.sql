ALTER TABLE email.delivery_events DROP CONSTRAINT delivery_events_status_check;
ALTER TABLE email.delivery_events ADD CONSTRAINT delivery_events_status_check
    CHECK (status IN ('pending', 'sending', 'retry', 'sent', 'unknown', 'dead', 'redriving'));
ALTER TABLE email.delivery_events ADD COLUMN redrive_started_at timestamptz;
