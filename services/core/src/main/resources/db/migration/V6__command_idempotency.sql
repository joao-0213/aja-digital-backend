CREATE TABLE core.command_idempotency (
    actor_id varchar(128) NOT NULL REFERENCES core.users(id),
    operation varchar(80) NOT NULL,
    target varchar(160) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    response_body text NOT NULL,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY(actor_id, operation, target, idempotency_key),
    CHECK (expires_at > created_at)
);
CREATE INDEX command_idempotency_expiry_idx ON core.command_idempotency(expires_at);
