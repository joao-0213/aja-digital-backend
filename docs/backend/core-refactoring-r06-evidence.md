# Core refactoring R-06 — Isolate outbox

**Starting revision:** `b2e756b` (`codex/backend-b01-b16`)

## Summary of changes
Split `OutboxPublisher.kt`, `OutboxRedrive.kt`, and `OutboxEncryption.kt` into focused ports, use cases, adapters, scheduler, and configuration under `outbox/`:
- Incoming ports (`outbox/domain/ports/in/`): `PublishPendingEventsUseCase`, `RedriveOutboxEventUseCase`.
- Outgoing ports (`outbox/domain/ports/out/`): `OutboxRepository`, `EventPublisher`, `OutboxPayloadProtector`.
- Use cases (`outbox/domain/usecases/`, zero Spring/JDBC/RabbitMQ/Jakarta imports): `PublishPendingEvents` (uses `TransactionRunner`, `OutboxRepository`, `EventPublisher`, `Clock`), `RedriveOutboxEvent`.
- Adapters & configuration: `OutboxScheduler` (`outbox/adapters/in/scheduling/`), `OutboxOperationsController` (injects `RedriveOutboxEventUseCase`), `JdbcOutboxRepository` (`outbox/adapters/out/persistence/`), `RabbitEventPublisher` (`outbox/adapters/out/messaging/`), `IdentityEventsTopology` (`EVENTS_EXCHANGE` moved into `companion object`), `AesGcmOutboxPayloadProtector` (`outbox/adapters/out/security/`), `OutboxConfiguration` (`outbox/configuration/`).
- Updated `OutboxPayloadProtector` callers (`IdentityConfiguration`, `ClassroomConfiguration`, `RegisterStudent`, `RequestAuthToken`, `CreateUser`, `JdbcAnnouncementStore`) and test files (`CoreIntegrationTestBase`, `OutboxIntegrationTests`, `DashboardIntegrationTests`, `ArchitectureTests`).
- Added unit tests `PublishPendingEventsTests` and `AesGcmOutboxPayloadProtectorTests`.

## Verification
- Targeted tests (`PublishPendingEventsTests`, `AesGcmOutboxPayloadProtectorTests`, `ArchitectureTests`, `OutboxIntegrationTests`): exit `0`.
- `.\gradlew.bat test --rerun-tasks --quiet --console=plain` (`B16_OPENAPI_COVERAGE` unset): exit `0` (`24` suites, `156` tests, `0` failures, `0` errors, `0` skipped).
- `$env:B16_OPENAPI_COVERAGE = 'true'; .\gradlew.bat build --rerun-tasks --quiet --console=plain` (cleared after): exit `0` (`24` suites, `156` tests, `0` failures, `0` errors, `0` skipped; `CoreOpenApiCoverageFilter` verified all `95` non-`/questions` operations in `docs/http/openapi.json`).

## Review record
- Independent ECC review (`r06-ecc`): `APPROVE` (`0` CRITICAL/HIGH/MEDIUM/LOW).
- Independent Ponytail review (`r06-ponytail`): `APPROVE` (`Looks good. Ship.`).
- Independent Database review (`r06-db`): `APPROVE` (`0` findings).
