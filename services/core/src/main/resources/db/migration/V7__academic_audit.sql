ALTER TABLE core.audit_events
    DROP CONSTRAINT audit_events_event_type_check;

ALTER TABLE core.audit_events
    ADD CONSTRAINT audit_events_event_type_check CHECK (event_type IN (
        'auth.registration',
        'auth.login',
        'auth.logout',
        'auth.email_confirmation_request',
        'auth.password_reset_request',
        'auth.email_confirmation',
        'auth.password_reset',
        'auth.profile_update',
        'academic.user_created',
        'academic.user_updated',
        'academic.user_role_changed',
        'academic.user_deactivated',
        'academic.user_withdrawn',
        'academic.class_created',
        'academic.class_updated',
        'academic.class_closed',
        'academic.class_reopened',
        'academic.enrollment_created',
        'academic.enrollment_activated',
        'academic.enrollment_cancelled',
        'academic.enrollment_ended'
    ));

ALTER TABLE core.audit_events
    ADD COLUMN actor_user_id varchar(128) REFERENCES core.users(id);

CREATE INDEX audit_events_actor_idx ON core.audit_events(actor_user_id, occurred_at);
