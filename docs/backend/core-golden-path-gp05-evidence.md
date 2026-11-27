# GP-05 evidence — Health, web boundaries and outbox

## Preflight

- Starting revision `8674ea6acb433cd7f2e43138487cf98ffe95e2d8` on `codex/core-golden-path`. The Git index and Core service tree were clean. Existing documentation and repository changes are outside this issue.
- `graphify query 'HealthController HealthControllerTests OriginFilter OriginFilterTests SharedExceptionHandler OutboxIntegrationTests R-06 R-07 GP-05' --budget 1000` located the health, Origin, error, session and outbox checks.
- Scope covers the public health read, shared browser and error handling, and the existing Core outbox delivery and private redrive protocol. It includes the producer’s atomic write, cipher and broker envelope, confirms, retry timing and redrive, and at-least-once publication across a failed database acknowledgement.
- Java 21 and the Gradle wrapper are available. The real PostgreSQL and RabbitMQ Testcontainers images and the Questions boot JAR are available for the Core checks.
- Baseline `./gradlew test --tests '*HealthControllerTests' --tests '*OriginFilterTests' --tests '*OutboxIntegrationTests' --tests '*AesGcmOutboxPayloadProtectorTests' --tests '*SessionAuthPrecedenceTests' --console=plain` in `services/core` passed 17 tests with zero failures, errors or skips in 38 seconds. It exercised the current encrypted event, RabbitMQ confirms and redrive, both health statuses, browser Origin decisions and session precedence.

## Acceptance

| Criterion | Status |
| --- | --- |
| Health controller, application and probe adapter preserve HTTP and dependency outcomes | Verified by contract checks and live health reads |
| Origin, session and shared error handling preserve cookie, CORS, status and error shape | Verified by focused and integration tests |
| Core outbox preserves cipher, broker confirms, retries, redrive and at-least-once publication | Verified by focused and integration tests |
| Shared code remains technical and feature code consumes shared ports | Verified by source inspection and architecture checks |
| Live focused checks, full Core test and build, Graphify and independent reviews | Verified on the final candidate |

## Validation

- The health ports now sit in the application layer, and the no-argument health calls preserve the PostgreSQL connection check and RabbitMQ passive exchange probe. The health contract test checks 200 with status and version, and 503 with the existing code, message and request ID.
- Origin and session precedence tests preserve the accepted and rejected origins, CORS headers, preflight behavior, and authentication error order. The forced PostgreSQL outbox insert failure returns the existing safe `PERSISTENCE_FAILED` payload without exposing the trigger error, and it rolls back the producer writes.
- The outbox operation ports now sit in the application layer. Real PostgreSQL and RabbitMQ tests cover redrive, confirms, rejection of unroutable events and republishing when marking a confirmed event fails. Retry checks cover the first delay, fifth failure, and the class-only error label. AES-GCM tests check the ciphertext, associated data and key identifier.
- Focused checks passed on the candidate. `./gradlew test --rerun-tasks --console=plain` and final `./gradlew test build --rerun-tasks --console=plain` in `services/core` passed. The final run took 6m08s and recorded 234 tests across 46 suites with zero failures, errors or skips.
- `./gradlew architectureAudit --console=plain` reran after the package moves. The report contains 47 Core findings and no health or outbox findings. Remaining strict contract and placement checks belong to GP-07. `graphify update .`, scoped whitespace checks, and the OpenAPI, HTTP contract and migration diff checks passed.

## Independent reviews

- The ECC reviewer approved without findings after checking the health and outbox moves, wire outcomes, and the publication, cipher, transaction, retry and redrive behavior.
- The registered PostgreSQL reviewer approved without findings. The health probe still checks the connection and passive exchange and closes its resources. The reviewed publisher, repository and redrive logic differ only in imports. The reviewer confirmed the SQL, bindings, isolation, 1/2/4/8/16-second backoff, unchanged encrypted payload, redrive and rollback behavior.
