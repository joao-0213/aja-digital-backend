# GP-04.4 evidence — Attempts

Starting revision `c64dadec023055e52a169213f4e59be9859bb781` on `codex/core-golden-path`. GP-03 and GP-04.3 were committed. The checkout contained pre-existing repository and documentation edits. GP-04.4 covers the six `/attempts` operations and the decision exposed at `POST /internal/v1/session/practice`.

## Preflight

- `graphify query 'AttemptPolicy JdbcAttemptStore StartAttemptUseCase SaveAttemptAnswersUseCase SubmitAttemptUseCase PracticeBlockOutputPort' --budget 850` located the attempt policies, JDBC store, use cases and store port. The ownership ledger records the authorization facts and data read by that store. The public OpenAPI and HTTP behavior contract define attempt projections, status codes and answer replacement.
- OpenJDK 21 and Docker engine 29.9.3 were available. The Core Gradle wrapper uses JUnit, ArchUnit and Testcontainers. The PostgreSQL, RabbitMQ and Ryuk images were available locally. The practice integration test launches independent Core and Questions jar processes and exercises the authenticated private HTTP endpoint.
- The pre-existing demo seed contains submitted attempts with an explicit `image: null` in their snapshots and a nullable attempt-level `questions_updated_at`. The V12 table constraint ties a submitted timestamp to its stored receipt and score. V6 stores the idempotency response as text, so nested receipt field order is observable during replay.

## Acceptance

| Criterion | State |
| --- | --- |
| Typed attempt state and incoming/outgoing contracts | Verified by compilation and the mandatory attempt port rule |
| Pure snapshot scoring and deadline/transition decisions | Verified by focused domain and integration tests |
| Direct authorization/invariant tests and adapter contracts | Verified by input-port, collection alias and live adapter tests |
| HTTP behavior, locking, idempotency and legacy receipts | Verified by focused PostgreSQL and Core–Questions process tests |
| Full Core test/build, Graphify refresh and independent reviews | Verified. Core-wide architecture findings remain tracked under GP-07 |

## Boundaries

- Assessment owns the six public operations for attempts and the practice decision at `POST /internal/v1/session/practice`. The private practice adapter resolves the actor via Identity's input port. Moving its inbound route removed the Identity-to-Assessment dependency. Identity remains the owner of `GET /internal/v1/session`.
- The store keeps the existing transaction, session and assignment gates. Typed state and question snapshots now support the same revision, deadline and scoring decisions. A shared adapter mapper uses the original receipt representation for HTTP and idempotency storage.

## Validation

- Baseline `gradlew.bat test --tests '*AssessmentIntegrationTests' --tests '*Attempt*' --tests '*IdentityIntegrationTests' --tests '*Practice*' --console=plain` exited 0 in 5m23s. XML records 50 tests across three suites with zero failures, errors or skips. This includes running Core and Questions HTTP processes, PostgreSQL and RabbitMQ.
- Architecture RED `gradlew.bat test --tests 'br.com.ajadigital.core.assessment.AttemptFeatureBoundaryTests' --console=plain` exited 1. ArchUnit reported four dependencies from `InternalSessionController` to Assessment's practice port and request. Repeating the command after transferring the practice route to Assessment exited 0 in 19s with one test passed.
- Practice and attempt integration checkpoint `gradlew.bat test --tests '*AssessmentIntegrationTests' --tests '*Practice*' --console=plain` exited 0 in 5m48s. All 27 tests passed. The Core and Questions processes exercised the same authenticated practice route, including authorization changes queued behind the advisory lock.
- Source comparison against the starting revision found the same 12 SQL statement literals, seven transaction annotations (three read-only), one `FOR UPDATE`, one `FOR SHARE`, and one advisory lock expression in `JdbcAttemptStore`. `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration` exited 0.
- `gradlew.bat compileKotlin compileTestKotlin --console=plain` exited 0 after the typed package and port moves. The focused test command below exited 0 with 23 tests and no failures, errors or skips. The mandatory ArchUnit selector checks all seven attempt input ports and the store output port for concrete contracts, and the policy tests cover the exclusive deadline and snapshot scoring.

```powershell
gradlew.bat test --tests 'br.com.ajadigital.core.CoreArchUnitTests' --tests 'br.com.ajadigital.core.CoreArchitectureFixtureTests' --tests 'br.com.ajadigital.core.assessment.AttemptFeatureBoundaryTests' --tests 'br.com.ajadigital.core.assessment.domain.models.AttemptStateTests' --tests 'br.com.ajadigital.core.assessment.domain.policies.AttemptPolicyTests' --tests 'br.com.ajadigital.core.assessment.domain.policies.AttemptScoringPolicyTests' --tests 'br.com.ajadigital.core.assessment.domain.policies.DeadlinePolicyTests' --console=plain
```

- `graphify update .` exited 0. A subsequent query identified the Assessment-owned practice controller, the relocated application store port and the shared attempt representation. `git check-ignore graphify-out/graph.json` verified that the generated graph is local.
- The final typed integration candidate repeated `gradlew.bat test --tests '*AssessmentIntegrationTests' --tests '*Practice*' --console=plain` and exited 0 in 5m32s. XML records 27 tests with no failures, errors or skips. Start and submit replay match the exact stored `response_body` bytes. The submitted snapshot retains its explicit null image and resolved answer. The in-progress projection omits the answer key and explanation.
- `gradlew.bat test --tests 'br.com.ajadigital.core.assessment.SaveAttemptAnswersUseCaseTests' --tests 'br.com.ajadigital.core.assessment.domain.models.AttemptModelImmutabilityTests' --tests 'br.com.ajadigital.core.CoreArchUnitTests' --tests 'br.com.ajadigital.core.assessment.AttemptFeatureBoundaryTests' --console=plain` passed 18 tests. Direct calls verify forbidden actors and invalid revisions before store writes. The new models copy mutable input collections while preserving null answers and field order.
- Forced full gate `gradlew.bat test build --rerun-tasks --console=plain` exited 0 in 10m07s. The XML report records 212 tests across 38 suites with no failures, errors or skips. The invocation compiled production and test Kotlin, ran test and check, and built the executable Core jar.
- The separate diagnostic `gradlew.bat architectureAudit --console=plain` exited 1 with 132 findings, compared with 151 recorded at the end of GP-04.3. The Identity-to-Assessment edge is gone and the mandatory attempt contract rule passes. Core-wide findings remain, including the existing serializer reference in `AttemptPolicy`. Core-wide compliance remains tracked by GP-07.

## Reviews

- Independent ECC review by GPT-6.1 Terra at medium reasoning approved the complete issue diff with no actionable findings. The review used the recorded validation evidence.
- The registered `database-reviewer` approved the complete issue diff with no confirmed findings. The active host role and `.codex/database-reviewer.toml` fix GPT-6.1 Sol at medium reasoning. The repository guidelines request GPT-6.1 Terra for this role. The review inspected the codecs, SQL and bind order, and lock and transaction scope. Compatibility with pre-refactor stored documents was reviewed statically; the runtime replay tests used fixture documents.
