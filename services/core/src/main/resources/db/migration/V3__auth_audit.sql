CREATE TABLE core.audit_events (
    id varchar(36) PRIMARY KEY,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    event_type varchar(48) NOT NULL CHECK (event_type IN (
        'auth.registration',
        'auth.login',
        'auth.logout',
        'auth.email_confirmation_request',
        'auth.password_reset_request',
        'auth.email_confirmation',
        'auth.password_reset',
        'auth.profile_update'
    )),
    outcome varchar(16) NOT NULL CHECK (outcome IN ('success', 'failure', 'accepted', 'invalid', 'expired', 'conflict')),
    subject_user_id varchar(128)
);

CREATE INDEX audit_events_occurred_at_idx ON core.audit_events(occurred_at);
