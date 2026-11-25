# Core and Email delivery operations

Core publishes versioned confirmation, password-reset and announcement-notification events. RabbitMQ confirms each publish before Core sets `published_at`. A process crash after the broker confirmation and before that update can publish the same event again. Email deduplicates by event ID.

Core retries a failed publish up to five times with increasing delays. Events that exhaust retries remain encrypted in `core.outbox_events` with `failed_at` and a class-only `last_error`. Inspect metadata without selecting `encrypted_payload`, `nonce`, or any decrypted content:

```sql
select id, event_type, attempt_count, next_attempt_at, last_error
from core.outbox_events
where failed_at is not null and published_at is null;
```

An operator may redrive one failed event with `POST /internal/ops/outbox/{id}/redrive` and the `X-AJA-Operator-Token` secret. The operation accepts only UUIDs currently marked failed, clears retry metadata, and retains the same event ID and encrypted payload. It returns 202 when queued for retry, 401 for a missing/invalid operator token, and 404 when that event is not redrivable. Set `CORE_OUTBOX_OPERATOR_TOKEN` in Core's secret environment.

Email stores only event ID/type, attempt count, status, timestamps, and a class-only error label in `email.delivery_events`. Status `dead` also routes the broker message to `email.identity.dlq`. Transient provider failures use the bounded retry queue before the fifth failure becomes dead.

Before calling a provider, Email commits status `sending`. If the worker restarts with an event still in that state, or the provider times out after accepting the request, the state becomes `unknown` and the event is acknowledged without an automatic resend. In Resend and generic HTTP modes, use the event ID sent as `Idempotency-Key` to help reconcile with provider records. Mark it delivered only after provider evidence confirms acceptance. Exactly-once external delivery is not guaranteed.

The provider adapter posts the recipient, subject and plain-text message to `EMAIL_PROVIDER_URL`. Resend and generic HTTP modes send `EMAIL_PROVIDER_TOKEN` as a bearer secret when configured. `EMAIL_ACTION_BASE_URL` sets the frontend origin used in confirmation, recovery and announcement links. Supply credentials and the shared `CORE_OUTBOX_ENCRYPTION_KEY` through the environment or a secret store. See [Email delivery](local-development.md#email-delivery) for configuration and local acceptance.

An operator can redrive one dead Email event with `POST /internal/ops/delivery/{eventId}/redrive` and `X-AJA-Operator-Token`, configured by `EMAIL_DELIVERY_OPERATOR_TOKEN`. The worker atomically claims only a `dead` event, republishes its original encrypted broker payload with publisher confirmation, and acknowledges its old DLQ copy only after confirmation. Repeated calls while a claim is active return 202 without a second replay; missing/non-dead event IDs return 404 and missing/invalid credentials return 401. If the worker crashes during replay, RabbitMQ requeues unacknowledged DLQ messages and the claim can be retried after its lease expires. The event ID and ciphertext are preserved, so Email deduplication continues to apply.

Run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\acceptance-b03-email-process-restart.ps1` to verify the real-process recovery path with a fresh Compose project and volume. It blocks the captured provider after Email has persisted `sending`, kills and restarts only the Email JVM, and verifies broker redelivery settles as `unknown` without another provider request. The script stops its own containers but keeps its volume; it does not touch other Compose projects or volumes.
