# Core refactoring R-03 — wire controllers through ports and extract use cases

**Issue:** R-03 (phase 4, first half). **Status:** acceptance criteria verified; evidence and the independent review record are below.
**Plan:** [core-refactoring-plan.md](core-refactoring-plan.md). **Characterization:** [core-refactoring-r00-characterization.md](core-refactoring-r00-characterization.md).

This document is evidence for R-03. It does not authorize behavior changes.

## Starting point

| Item | Value |
| --- | --- |
| Branch | `codex/backend-b01-b16` |
| Base commit | `1710533` (`refactor(core): move Core sources and tests into feature packages`) |
| Service | `services/core` (Kotlin 2.3.21, Spring Boot 4.1.2, Gradle 9.8.2, JDK 21) |
| JDK used | Azul 21.0.13 (`C:\Users\joaov\.jdks\azul-21.0.13.0.3`), set via `JAVA_HOME` |
| Docker | Testcontainers PostgreSQL 16 / RabbitMQ 4 |
| Graphify | `graphify-out/graph.json` present; queried before broad source reads |

## What changed

### Incoming ports and use-case wiring

- Created `16` incoming port interfaces under `<feature>/domain/ports/in/*UseCase.kt` across `identity` (`5`: `AuthenticateStudentUseCase`, `RegisterStudentUseCase`, `RequestAuthTokenUseCase`, `ResetPasswordUseCase`, `UpdateOwnProfileUseCase`), `classroom` (`ManageAcademicsUseCase`), `curriculum` (`ManageSubjectsUseCase`), `materials` (`ManageContentUseCase`), `assessment` (`3`: `ManageAssessmentsUseCase`, `ManageAttemptsUseCase`, `ManageSubmissionsUseCase`), `announcements` (`ManageAnnouncementsUseCase`), `forum` (`ManageTopicsUseCase`), `attendance` (`ManageAttendanceUseCase`), `dashboard` (`ManageDashboardUseCase`), and `health` (`CheckServiceHealthUseCase`). (`outbox` split remains deferred to R-06.)
- All `16` concrete use cases implement their respective incoming port interfaces and depend only on outgoing port interfaces (with `OutboxEncryption` in `ManageAcademics`, `RegisterStudent`, and `RequestAuthToken` deferred to R-06 as `OutboxPayloadProtector`).
- All `15` feature controllers (excluding `OutboxOperationsController`, deferred to R-06), `FileUploadSessionFilter`, `ManageDashboard`, and `CoreIntegrationTestBase` (`manageAcademics`) now depend on incoming port interfaces instead of concrete use-case classes.
- Created `11` feature/shared `@Configuration` classes (`AnnouncementsConfiguration`, `AssessmentConfiguration`, `AttendanceConfiguration`, `ClassroomConfiguration`, `CurriculumConfiguration`, `DashboardConfiguration`, `ForumConfiguration`, `HealthConfiguration`, `IdentityConfiguration`, `MaterialsConfiguration`, `SharedConfiguration`) to concentrate `@Bean` wiring.
- `CoreApplication.kt` stays at root `br.com.ajadigital.core` with `@SpringBootApplication`, `@EnableScheduling`, and `main`, with all `@Bean` methods removed.
- `@Repository` and `@Component` annotations preserved on persistence and adapter beans.

### Transaction boundary extraction (`TransactionRunner`)

- Added `TransactionRunner` (`shared/domain/ports/out/TransactionRunner.kt`) and `SpringTransactionRunner` (`shared/adapters/out/transaction/SpringTransactionRunner.kt`), wired via `SharedConfiguration`.
- Removed all `31` controller `@Transactional` annotations (`AnnouncementController` `3`, `AttendanceController` `2`, `ClassroomController` `13`, `CurriculumController` `6`, `ForumController` `6`, `ContentController` `1`) and replaced them with `transactionRunner.run { ... }` in the corresponding use-case write methods (`ManageAnnouncements`, `ManageAttendance`, `ManageAcademics`, `ManageSubjects`, `ManageTopics`, `ManageContent`).
- Preserved remote-call isolation: `ManageTopics.create` and `ManageAssessments.create`/`update` do **not** wrap `QuestionSnapshotProvider.snapshots` calls inside `TransactionRunner`. All lock orders, session revalidations, revision checks, idempotency windows, and rollback behavior match R-00 characterization.

### Tests

- Added `SpringTransactionRunnerTests.kt` (`4` unit tests verifying `ManageTopics.create` and `ManageAssessments.create`/`update` call `QuestionSnapshotProvider.snapshots` outside `TransactionRunner`, and `ManageContent.publish` and `ManageTopics.reply` execute inside `TransactionRunner` and roll back/propagate failure).
- Added `2` architecture tests in `ArchitectureTests.kt` verifying incoming port usage by controllers/`FileUploadSessionFilter`/`ManageDashboard`, absence of `@Transactional` on controllers, absence of `@Bean` in `CoreApplication.kt`, and presence of `TransactionRunner` / `SpringTransactionRunner`.
- Added `springTransactionRunnerRollsBackAtomicallyOnFailure()` in `PlatformIntegrationTests.kt` verifying real PostgreSQL commit and rollback through `TransactionRunner`.
- Updated `CoreIntegrationTestBase.kt` to autowire `ManageAcademicsUseCase`.

## Verification

### Mechanical checks

| Check | Result |
| --- | --- |
| Main `.kt` files | `211` total (`33` modified in place, `29` new: `16` `*UseCase.kt`, `11` `*Configuration.kt`, `TransactionRunner.kt`, `SpringTransactionRunner.kt`) |
| Test `.kt` files | `24` total (`3` modified in place, `1` new: `SpringTransactionRunnerTests.kt`) |
| One-top-level-type-per-file (`fileName == typeName`) | `PASS` — all `29` new main files and `1` new test file declare a single matching top-level type |
| `@Transactional` in controllers | `0` (all `31` removed) |
| Incoming port callers (`*Controller.kt` excl. `OutboxOperationsController`, `FileUploadSessionFilter`, `ManageDashboard`, `CoreIntegrationTestBase`) | `0` concrete `.domain.usecases.*` type dependencies (only pre-existing `toResponse` extension import in `AuthController.kt`) |
| Use-case outgoing dependencies | Only outgoing port interfaces, except `OutboxEncryption` in `ManageAcademics`, `RegisterStudent`, `RequestAuthToken` (deferred to R-06) |
| Non-`.kt` files changed (`build.gradle`, `application.properties`, `db/migration/*`, `docs/http/openapi.json`) | `0` (`git diff --name-only HEAD -- ":(exclude)*.kt"` empty) |
| SQL / migration changes | `0` |
| Speculative abstractions (`BaseUseCase`, `GenericService`, `AbstractRepository`) | `0` |

### Commands

Run from `services/core` with `JAVA_HOME=C:\Users\joaov\.jdks\azul-21.0.13.0.3` after final edits:

| # | Command | Exit | Outcome |
| --- | --- | --- | --- |
| 1 | `.\gradlew.bat test --tests "br.com.ajadigital.core.shared.adapters.out.transaction.SpringTransactionRunnerTests" --console=plain` | `0` | `BUILD SUCCESSFUL in 47s`; `4` tests (`SpringTransactionRunnerTests`), `0` failures, `0` errors, `0` skipped. |
| 2 | `.\gradlew.bat test --rerun-tasks --console=plain` (`B16_OPENAPI_COVERAGE` unset) | `0` | `BUILD SUCCESSFUL in 7m 48s`; `7 actionable tasks: 7 executed`; JUnit XML total: `131` tests, `0` failures, `0` errors, `0` skipped across `16` classes (`AnnouncementsIntegrationTests` `6`, `ArchitectureTests` `5`, `HttpQuestionSnapshotProviderTests` `1`, `AssessmentIntegrationTests` `23`, `AttendanceIntegrationTests` `4`, `CurriculumIntegrationTests` `18`, `DashboardIntegrationTests` `7`, `ForumIntegrationTests` `16`, `HealthControllerTests` `1`, `SessionCookieTests` `1`, `IdentityIntegrationTests` `20`, `MaterialsIntegrationTests` `8`, `OutboxIntegrationTests` `4`, `PlatformIntegrationTests` `6`, `OriginFilterTests` `7`, `SpringTransactionRunnerTests` `4`). |
| 3 | `.\gradlew.bat build --rerun-tasks --console=plain` with process-scoped `$env:B16_OPENAPI_COVERAGE = 'true'` (cleared after) | `0` | `BUILD SUCCESSFUL in 6m 55s`; `7 actionable tasks: 7 executed`; JUnit XML total: `131` tests, `0` failures, `0` errors, `0` skipped across `16` classes; `CoreOpenApiCoverageFilter.assertEveryOperationHasSuccessAndExpectedDenial()` passed for all `95` non-`/questions` operations in `docs/http/openapi.json`. |

### Non-changes

- No HTTP operation, path, status code, response schema, cookie, Origin rule, or idempotency behavior changed; `docs/http/openapi.json` is untouched.
- No SQL, migration, `build.gradle`, or `application.properties` change; no new dependency.

## Known limits

- **Outbox & crypto deferrals (R-06 scope).** `OutboxOperationsController` -> `OutboxRedrive` and `ManageAcademics` / `RegisterStudent` / `RequestAuthToken` / `JdbcAnnouncementStore` -> `OutboxEncryption` remain deferred to R-06 (`OutboxPayloadProtector` / `RedriveOutboxEventUseCase`).
- **Environment.** Suite executed locally on Windows with Docker Desktop (PostgreSQL 16 and RabbitMQ 4 Testcontainers), not in CI.

## Review record

Independent read-only reviews were run against the complete R-03 diff against base commit `1710533`, with targeted rechecks by the same three reviewers after the confirmed test fix and post-final-edit Gradle validation.

| Reviewer | Verdict | Findings and disposition |
| --- | --- | --- |
| Database reviewer (`.codex/database-reviewer.toml`) | **APPROVE** | 0 CRITICAL/HIGH/MEDIUM findings (`TransactionRunner` / `SpringTransactionRunner` `PROPAGATION_REQUIRED` boundaries, lock ordering, atomicity, and remote snapshot isolation in `ManageTopics.create` / `ManageAssessments.create`/`update` verified identical to R-00; 0 SQL/migration/config diffs). Round 1 LOW (`SpringTransactionRunnerTests.kt:121` test naming vs `ManageTopics.reply` coverage) — **fixed** (`SpringTransactionRunnerTests.kt:120-239`) and verified in targeted recheck. |
| ECC code reviewer (`docs/agents/code-reviewer.md`) | **APPROVE** | 0 CRITICAL/HIGH/MEDIUM findings (all 16 incoming ports, 11 `*Configuration` classes, removal of all 31 controller `@Transactional` annotations, one-type-per-file rule, and JUnit XML results verified). Round 1 LOW (`SpringTransactionRunnerTests.kt:121` test naming overstating body) — **fixed** and verified in targeted recheck. |
| Ponytail reviewer (`.agents/skills/ponytail-review/SKILL.md`) | **Ship** | Round 1 Should-fix #1 (`SpringTransactionRunnerTests.kt:L120-174` claiming `ManageTopics.reply` coverage without calling it) — **fixed** (split `ManageContent.publish` test and added dedicated `ManageTopics.reply` `TransactionRunner` rollback test) and verified in targeted recheck (`131` tests passing across `16` suites). |
