# Core refactoring plan

**Status:** all phases and acceptance criteria are **pending**. This is a concrete plan for future authorized Core work; it does not implement a backlog issue, establish current compliance, or authorize a refactor. The current API and service contracts remain governed by [ARQUITETURA.md](../../ARQUITETURA.md), [docs/backend/operation-ownership.md](operation-ownership.md), issue acceptance criteria, and [docs/http/openapi.json](../http/openapi.json). Target package rules are in [ARCHITECTURE.md](../../ARCHITECTURE.md).

## Scope and constraints

Refactor Core only. Keep Questions and Email unchanged except for already-authorized contract-driven work in their own issues. Preserve the existing `core` schema, migrations, JDBC store behavior, public API, private service interfaces, and outbox delivery semantics. This plan does not authorize a JPA migration, schema change, new public operation, frontend integration, gateway, or implementation of any B-01–B-16 issue.

Core's current responsibilities group into these functional packages:

| Feature package | Current responsibility |
| --- | --- |
| `identity` | Registration, login/session, profile, confirmation and reset tokens, internal session/practice decisions, password hashing |
| `classroom` | Users, roles, classes, students, enrollments and academic actor context |
| `curriculum` | Subjects, modules, ordering and visits |
| `materials` | Files, materials, upload state, progress and file access policy |
| `assessment` | Authoring, immutable question snapshots, attempts, answers, results, submissions and feedback |
| `announcements` | Announcements, calendar projections and notification requests |
| `forum` | Topics, replies, targets, votes, read state and solutions |
| `attendance` | Attendance sessions and records |
| `dashboard` | Role-scoped dashboard projections |
| `outbox` | Event encryption, publication, retry/redrive and operational access |
| `health` | Local service readiness |

Assessment authoring, attempts, and submissions stay within one Core `assessment` feature because edits reset drafts and submitted work must be inspected atomically under shared authorization/revision rules. They may have separate incoming ports, use cases, outgoing ports, and stores. Cross-feature SQL joins needed for authorized projections remain inside Core and behind explicit outgoing ports. Do not introduce adapter-to-adapter calls or cross-schema reads.

## Current source inventory and target ownership

All paths below are relative to `services/core/src/main/kotlin/br/com/ajadigital/core/`, unless noted. Move existing types into the named feature, then split co-located types into one type per matching file. Package moves retain current public/private API behavior and SQL; extraction is mechanical until separately authorized behavior work.

| Current file(s) | Target feature and extraction |
| --- | --- |
| `AuthController.kt` | `identity`: separate browser auth, self/profile, and internal session controllers under `adapters/in/controllers`; split each request/response DTO under `adapters/in/controllers/models/{requests,responses}`; split auth exception handlers under `handlers`; move `InvalidInternalServiceCredential` to identity exceptions. |
| `RegisterStudent.kt` | `identity`: separate registration use case, registration command/result, encrypted payload, and outbox encryption into matching `domain/usecases`, `models/{requests,responses}`, and policy/crypto adapter types. Keep cryptographic implementation behind an explicit port if use cases require it. |
| `AuthenticateStudent.kt` | `identity`: separate authentication/session incoming port, use-case implementation, login command, authenticated session output, session response, and exception types. |
| `ManageAuthTokens.kt` | `identity`: split request-token and password-reset use cases; move token/reset commands, accepted response, and failure types to matching files. |
| `UpdateOwnProfile.kt` | `identity`: split incoming port, implementation, edit command, and revision conflict. |
| `User.kt`, `EmailAddress.kt` | `identity/domain/models` and `identity/domain/policies` respectively, preserving normalization and validation semantics. |
| `IdentityStore.kt`, `PasswordHasher.kt`, `IdempotencyStore.kt`, `JdbcIdentityStore.kt`, `Argon2PasswordHasher.kt`, `JdbcIdempotencyStore.kt` | `identity` or the narrowly owned technical feature: one type per file under `domain/ports/out` and `adapters/out/{persistence,security}`. Idempotency semantics remain shared only where current contracts establish a common technical capability. |
| `AcademicController.kt` | Split by `classroom`, `curriculum`, `forum`, and `attendance` into feature controllers; extract each request DTO to matching controller `models/requests`; move exception mapping into feature handlers. Keep `TopicTargetRequest` and topic commands under `forum`; attendance DTOs under `attendance`. |
| `ManageAcademics.kt`, `AcademicStore.kt`, `JdbcAcademicStore.kt` | `classroom`: split incoming port, use cases, `AcademicActor`, page/provisioning models, and SQL adapter types. Keep academic authorization and identity/enrollment transitions in Core. |
| `ManageSubjects.kt`, `SubjectStore.kt`, `JdbcSubjectStore.kt` | `curriculum`: separate incoming port, use case, exceptions, store port, and JDBC adapter. |
| `ManageContent.kt`, `ContentStore.kt`, `JdbcContentStore.kt` | Split `ManageContent` between `materials` use cases; move file/material/progress ports and models to `materials/domain/ports/out`; place `JdbcContentStore` under `materials/adapters/out/persistence`. Keep policy checks tied to the owning resource and current session. |
| `ContentController.kt` | `materials`: split HTTP controller, upload/session filter, and material/progress request DTOs into controller, filter/security adapter, and DTO files. Preserve upload ownership, content types, route behavior, and the private Questions file-provisioning path. |
| `AnnouncementController.kt`, `ManageAnnouncements.kt`, `AnnouncementStore.kt`, `JdbcAnnouncementStore.kt` | `announcements`: separate controller, request/response types, use-case port/implementation, occurrence/draft models, persistence port and JDBC adapter. Keep calendar projection with the feature that owns the source announcement data. |
| `AssessmentController.kt` | `assessment`: split assessment authoring, attempt, and submission controllers; all HTTP DTOs and handlers into matching files. Preserve operation paths, `aja_session`, idempotency headers, status codes and response schemas. |
| `ManageAssessments.kt`, `ManageAttempts.kt`, `ManageSubmissions.kt`, `AssessmentStore.kt`, `JdbcAssessmentStore.kt`, `JdbcAttemptStore.kt`, `JdbcSubmissionStore.kt` | `assessment`: split each incoming port and use case; split `AssessmentStore.kt`'s `AssessmentStore`, `AttemptStore`, `SubmissionStore`, `QuestionSnapshotProvider`, their models and snapshot types into one matching type per file. Keep three JDBC adapters distinct where that preserves transaction/locking boundaries. |
| `HttpQuestionSnapshotProvider.kt` | `assessment/adapters/out/questions`: keep authenticated private client separate from public transport and retain revision/hash/image validation. |
| `ManageTopics.kt`, `TopicStore.kt`, `JdbcTopicStore.kt` | `forum`: split incoming port/use case/exceptions, `TopicDraft`, store port, and persistence adapter. The question-reference validation remains an outgoing private Questions capability; material access remains Core-owned. |
| `ManageAttendance.kt`, `AttendanceStore.kt`, `JdbcAttendanceStore.kt` | `attendance`: split incoming port/use case/exceptions, draft and store port, and JDBC adapter. |
| `ManageDashboard.kt` | `dashboard`: define one incoming port/use case and an explicit outgoing read-projection port; any SQL joining Core-owned feature data stays in its implementing adapter and remains scoped/bounded. |
| `HealthController.kt`, `CheckServiceHealth.kt`, `ServiceReadiness.kt`, `HealthProbe.kt`, `DatabaseAndBrokerHealthProbe.kt` | `health`: separate HTTP controller/response, incoming health port/use case, readiness model, outgoing probe port, and adapter. Keep health routes and dependency semantics from the current contract. |
| `OutboxPublisher.kt`, `IdentityEventsTopology.kt` | `outbox`: split the publisher, scheduler, redrive use case/port, persistence port/adapter, RabbitMQ topology/publisher, operational controller/request/response, and encryption implementation into matching files/packages. Preserve payload cipher/AAD, key selection, transaction, confirms, retries, at-least-once behavior and operational authorization. |
| `OriginFilter.kt` | `identity/adapters/in/security` or service-level `configuration/security`, according to the actual filter ownership: keep this cross-cutting HTTP boundary outside the pure functional core and preserve allowed-Origin behavior. |
| `CoreApplication.kt` | `configuration`: keep only application entry point and component configuration; move beans/wiring into explicit feature or service configuration when this file currently contains them. |

Also inventory every top-level, nested, private, and test-only type before extraction. This list reflects declarations found in the current sources; it is not permission to add abstractions for every item. A private helper that can remain local without violating the one-type-per-file rule is split into its own named file; a helper with no durable responsibility can be deleted only when the behavior remains covered.

### Test and build inventory (paths relative to repository root)

| Current file | Planned destination / treatment |
| --- | --- |
| `services/core/src/test/kotlin/br/com/ajadigital/core/CoreApplicationTests.kt` | Split test configuration, filters, fixtures, fake providers, clocks, and test-support types into single-type test-support files. Split integration tests by feature and keep process/database contract scenarios with their feature. Preserve all existing cases and aggregate coverage behavior used by `scripts/acceptance-b16.ps1`. |
| `services/core/src/test/kotlin/br/com/ajadigital/core/ArchitectureTests.kt` | Update package discovery and add dependency/file-shape checks matching the final package tree; keep source-aware one-type-per-file verification. |
| `services/core/src/test/kotlin/br/com/ajadigital/core/HealthControllerTests.kt`, `OriginFilterTests.kt`, `SessionCookieTests.kt` | Move beside their feature adapter packages, keeping names and behavior. |
| `services/core/src/test/kotlin/br/com/ajadigital/core/adapter/out/questions/HttpQuestionSnapshotProviderTests.kt` | Move with the private snapshot client adapter tests. |
| `services/core/build.gradle` | Only if separately authorized: add Boot-managed validation dependency and compatible springdoc MVC UI dependency, plus a source-aware architecture checker if selected. Pin/resolve versions through Boot dependency management and official compatibility docs; do not add dependencies as part of mechanical moves. Preserve current Gradle/JDK/Kotlin versions unless an authorized issue requires a change. |
| `services/core/src/main/resources/application.properties` | Add only explicitly authorized validation/OpenAPI profiles and settings. Default docs/UI disabled; keep public docs profile opt-in and private/internal routes excluded and disclosure-tested. |
| `services/core/src/main/resources/db/migration/*.sql` | No planned move or edit. The Core schema and existing migration history are retained. |

## Phased execution and acceptance checklist

Each phase must be authorized before implementation. Begin each authorized phase with the actual relevant issue criteria, dependencies, current Git state and Graphify query. All checks listed here are **pending** until run and recorded for that phase; no historical output is proof for a future refactor.

| Phase | Concrete work and affected files | Acceptance criteria |
| --- | --- | --- |
| 0. Characterize | Inspect all files in the inventory, every declared type and caller; capture current Core test/build and B-16 acceptance behavior before moves. Map each operation to its current controller and each controller to its use cases/stores. | **Pending:** record current revision, exact commands/outcomes, API/private operations touched, transaction/lock/idempotency/outbox invariants, declaration-to-destination map. Block on unavailable required tools/services rather than guessing. |
| 1. Split co-located types | First split controller DTOs/handlers, use-case commands/results/exceptions, ports/models, outbox types, and test support from their current files. Keep package and runtime wiring behavior fixed in this phase. | **Pending:** every named type has one matching source file; imports/callers compile; current focused Core tests and `.\gradlew.bat test` pass with no contract change. |
| 2. Move files by feature | Move types into feature `domain` and `adapters` packages using the ownership table; move controllers/workers to incoming adapters, SQL/JDBC to outgoing persistence adapters, private Questions client to its outgoing integration. Update imports, wiring, source-based architecture tests, and test paths. | **Pending:** use cases call only outgoing ports; controllers/workers call incoming ports; no adapter-to-adapter dependency; no cross-service/schema dependency; existing SQL, migrations, operations and error mapping remain unchanged. Run focused tests, Core `test` and `build`. |
| 3. Separate HTTP models and validation | Keep HTTP request/response types in controller adapters; add Bean Validation through Spring Boot's starter only when individual route contracts and error mapping are characterized. Keep the current use-case/domain models independent. | **Pending:** malformed, missing, null, unknown-field, and PATCH inputs retain the required precedence, status and error schema; auth/session checks still occur before validation where contracted; exact behavior covered by HTTP tests. Add only constraints with contract evidence. |
| 4. Extract deterministic use cases and transaction boundaries | Slice one feature and one behavior at a time from JDBC orchestration into incoming ports/use cases/policies. Add a transaction-running port only for an atomic boundary that spans stores; implement it with existing Spring transaction management. Keep SQL authorization/pagination in ports' persistence adapters. | **Pending:** focused characterization/regression test proves unchanged behavior; each write's actor/session/revision/idempotency revalidation, lock order, read locks, rollback and atomicity remain intact; remote snapshots occur outside broader DB transactions. Run real PostgreSQL/RabbitMQ process checks required by the owning issue. |
| 5. Isolate outbox and support capabilities | Split outbox schedule, decision, persistence, Rabbit publisher, cryptography and operational HTTP types. Move health and Origin filters into their adapter/configuration boundaries. | **Pending:** business row and outbox row commit/rollback together; cipher/AAD/key format unchanged; confirms/retry/redrive/restart behavior and at-least-once window remain observable; no credential/token leakage. Health/Origin/session checks retain existing outcomes. |
| 6. Add runtime API docs, if authorized | Add the Boot-compatible springdoc MVC UI dependency, public Core group and opt-in docs profile. Keep the committed OpenAPI file normative; do not include private service, outbox or operations routes in the public group. | **Pending:** compare generated public schema against the normative contract; verify every enabled docs JSON/YAML endpoint and UI, including ungrouped defaults, for private paths, schemas, operational APIs and secret disclosure. Confirm default docs/UI are disabled outside the opt-in profile and UI relies on normal cookie login. |
| 7. Final Core and aggregate gates | Run focused feature and architecture/source-shape checks, Core `test` and `build`, affected real-process acceptances, then `scripts/acceptance-b16.ps1` when the full backlog state and environment make that gate applicable. Review complete authorized diff and fix only confirmed findings. | **Pending:** record exact commands, exit outcomes, service/dependency availability, operation counts and any untested limits. No contract, migration, service ownership or unrelated files changed; only then mark the authorized issue's criteria verified. |

## Command references

Current service commands documented in [local-development.md](local-development.md) are run from `services/core`:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

The aggregate acceptance script is `scripts/acceptance-b16.ps1`; it currently checks the public OpenAPI operation count and references the real service suites and process acceptances. Use it only when the relevant acceptance criteria and required Docker/JDK/dependencies are available. These commands describe existing tools; this plan does not claim they have been run for the future refactor.

## Refactoring issues

The issues below are concrete work items that implement phases 0–7 of the phased execution checklist above. Each issue must be authorized before implementation. File-level scope references the [current source inventory](#current-source-inventory-and-target-ownership) and [test and build inventory](#test-and-build-inventory-paths-relative-to-repository-root) tables; those tables are not repeated here. This plan does not authorize implementation of any B-01–B-16 issue.

**Hard rules for every R-issue:**

- **No logic changes.** No new `if` statements, business rules, authorization checks, or error codes.
- **No SQL changes.** Every query stays character-for-character identical.
- **No new HTTP endpoints or changed response shapes.**
- **No new abstractions "for the future."** No `BaseUseCase`, `GenericService`, `AbstractRepository`.
- **No Spring Modulith framework adoption.** Use the principle of exposed interfaces; do not add the dependency.
- **Prefer established libraries over hand-rolled code** for: HTTP input validation (Bean Validation), API documentation (springdoc-openapi), architecture verification (ArchUnit). These are tools, not architectural changes.
- **Keep native solutions that are genuinely better**: `Argon2PasswordHasher` behind `PasswordHasher` port; `AesGcmOutboxPayloadProtector` behind `OutboxPayloadProtector` port; `TransactionRunner` port with `SpringTransactionRunner`; `EmailAddress` as a domain value type; JDBC persistence with explicit SQL; `OriginFilter`; domain policies as Kotlin code; `IdempotencyStore`/`JdbcIdempotencyStore`.
- **Preserve transaction boundaries exactly.** Outbox rows commit with business rows; remote snapshot fetches stay outside Core transactions; lock acquisition order unchanged; idempotency replay windows unchanged.
- **Do not add build dependencies during mechanical moves (R-01, R-02, R-03, R-05, R-06, R-07).** Dependencies are added only in their specific issues: `spring-boot-starter-validation` in R-04, `springdoc-openapi-starter-webmvc-ui` in R-08. Pin/resolve versions through Boot dependency management.

| Existing phase | Implementing issue(s) |
| --- | --- |
| 0. Characterize | R-00 |
| 1. Split co-located types | R-01 |
| 2. Move files by feature | R-02 |
| 3. Separate HTTP models and validation | R-04 |
| 4. Extract deterministic use cases and transaction boundaries | R-03, R-05 |
| 5. Isolate outbox and support capabilities | R-06, R-07 |
| 6. Add runtime API docs | R-08 |
| 7. Final gates | R-09 |

### R-00 — Characterize current state

**Implements:** phase 0. **Depends on:** none. **Status:** all criteria **pending**.

**Scope:** no file changes. Read-only inspection of every file in the source inventory, every declared type (top-level, nested, private, sealed subtypes) and every caller/import chain. Capture the current Core test/build baseline.

**What changes:** nothing. This issue produces only documentation artifacts:

1. Record starting Git revision.
2. Run `.\gradlew.bat test` and `.\gradlew.bat build` from `services/core`; record exact commands, exit codes, and output.
3. Map each public and private HTTP operation to its controller method.
4. Map each controller to the use-case/service classes it calls.
5. Map each use-case/service to the store ports and JDBC adapters it uses.
6. For each write operation, document: transaction scope, lock acquisition order, session revalidation points, revision/idempotency checks, rollback behavior, and whether remote calls (snapshot fetch, outbox publish) are inside or outside the transaction.
7. Record a declaration-to-destination map: every named type and its planned target feature/package from the source inventory table.

**Acceptance criteria:**

- **Pending:** recorded Git revision, exact `test` and `build` commands/outcomes, operation-to-controller-to-usecase-to-store map, transaction/lock/idempotency invariants per write operation, and declaration-to-destination map. Block on unavailable required tools/services rather than guessing.

---

### R-01 — Split co-located types into one-type-per-file

**Implements:** phase 1. **Depends on:** R-00. **Status:** all criteria **pending**.

**Scope:** all files in the source inventory that contain more than one named top-level type. Split controller DTOs, request/response types, exception types, sealed subtypes, handler classes, use-case commands/results, port interfaces, model classes, outbox types, and test-support types from their current files into individual files. Each file is named after its single top-level type.

Affected test files per the test inventory: split `CoreApplicationTests.kt` into single-type test-support files (test configuration, fixtures, fake providers, clocks) and per-feature integration test files. Keep every existing test case and its assertions.

**What changes:** file splits only. No package moves — types remain in their current packages. No new logic. No import changes beyond updating references from the old multi-type file to the new single-type files. Runtime wiring and Spring component scanning remain fixed.

**What does NOT change:** all observable HTTP behavior, SQL queries, transaction boundaries, lock orders, idempotency semantics, outbox guarantees, error shapes, and status codes. No functional changes to any type's implementation.

**Acceptance criteria:**

- **Pending:** every named type has one matching source file with `fileName == typeName`; no file contains more than one named top-level type (interfaces, enums, exception subtypes, sealed subtypes, and test support included); imports compile; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes; no contract or behavioral change.

---

### R-02 — Move files into feature packages

**Implements:** phase 2. **Depends on:** R-01. **Status:** all criteria **pending**.

**Scope:** move every type into its target feature package following the source inventory ownership table. Create the `domain/` and `adapters/` sub-trees per feature as specified in [ARCHITECTURE.md](../../ARCHITECTURE.md) — but only packages that have content. Do not create empty packages. Move controllers to `<feature>/adapters/in/controllers/`, JDBC adapters to `<feature>/adapters/out/persistence/`, `HttpQuestionSnapshotProvider` to `assessment/adapters/out/questions/`, and domain types (use cases, models, ports, exceptions) to `<feature>/domain/`.

Move test files per the test inventory: `HealthControllerTests.kt`, `OriginFilterTests.kt`, `SessionCookieTests.kt` beside their feature adapter packages; `HttpQuestionSnapshotProviderTests.kt` with its adapter; per-feature integration tests with their feature.

Update `ArchitectureTests.kt` package discovery to match the new tree structure (basic update only — hardened rules are in R-09).

**What changes:** package declarations, import statements, and test file locations. Update Spring component scanning or `@ComponentScan` base packages if currently hardcoded. No logic extraction — controllers still call implementation classes directly in this phase (port wiring is R-03).

**What does NOT change:** all SQL queries character-for-character; all HTTP operation paths, status codes, response schemas; all transaction boundaries, lock orders, and idempotency behavior; all migration files (no move, no edit); `build.gradle` (no new dependencies); `application.properties` (no changes).

**Acceptance criteria:**

- **Pending:** every type lives under its assigned feature package per the ownership table; no type remains in the old flat package structure; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes; architecture tests pass with updated package discovery; no adapter-to-adapter dependency; no cross-service/schema dependency; existing SQL, migrations, operations, and error mapping unchanged.

---

### R-03 — Wire controllers through ports and extract use cases

**Implements:** phase 4, first half (incoming/outgoing port wiring and use-case extraction from JDBC orchestration). **Depends on:** R-02. **Status:** all criteria **pending**.

**Scope:** for each feature, define incoming port interfaces in `domain/ports/in/` and outgoing port interfaces in `domain/ports/out/`. Use cases in `domain/usecases/` implement incoming ports. Persistence adapters, messaging adapters, and integration clients implement outgoing ports. Controllers call incoming ports, not concrete implementations. Each feature gets a `configuration/` class that wires port implementations via Spring.

Slice one feature and one behavior at a time from current `Manage*` classes into port/use-case pairs. The source inventory table specifies the extraction per file (e.g., `ManageAcademics.kt` → `classroom` incoming port + use cases + `AcademicActor` model; `ManageAssessments.kt`/`ManageAttempts.kt`/`ManageSubmissions.kt` → `assessment` incoming ports + use cases; etc.).

Add a `TransactionRunner` port in `shared/domain/ports/out/` with `SpringTransactionRunner` in `shared/adapters/out/transaction/` only for use cases that need to own an atomic boundary spanning multiple stores. Implement with Spring `TransactionTemplate` and the existing transaction manager.

**What changes:** class dependencies are rewired through interfaces. Controllers depend on port interfaces instead of concrete classes. Use cases depend on outgoing port interfaces instead of concrete JDBC adapters. Configuration classes provide the Spring wiring. `TransactionRunner` replaces direct `TransactionTemplate` usage in use cases where needed.

**What does NOT change:** all observable HTTP behavior; all SQL queries; transaction scope, rollback behavior, lock acquisition order, session revalidation, revision and idempotency checks, and required read locks per the R-00 characterization; remote snapshot fetches remain outside Core transactions; outbox writes remain in the same transaction as business changes. No new business rules or authorization logic.

**Acceptance criteria:**

- **Pending:** controllers depend on incoming port interfaces, not concrete use cases; use cases depend on outgoing port interfaces, not concrete adapters; adapters do not depend on one another; feature `configuration/` classes concentrate wiring; focused characterization/regression tests prove unchanged behavior for each extracted use case; each write operation's actor/session/revision/idempotency revalidation, lock order, and atomicity match R-00 characterization; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes.

---

### R-04 — Separate HTTP DTOs and add Bean Validation

**Implements:** phase 3. **Depends on:** R-03. **Status:** all criteria **pending**.

**Scope:** keep HTTP request/response types under `adapters/in/controllers/models/{requests,responses}` per feature. Keep domain models in `domain/models/`. Add mappers between HTTP DTOs and domain models at the controller boundary where the types differ. Add `spring-boot-starter-validation` to `services/core/build.gradle` via Boot dependency management.

Add Jakarta Bean Validation constraint annotations to HTTP request DTOs using Kotlin use-site targets (`@field:NotBlank`, `@field:Size`, `@field:Min`, `@field:Email`, etc.). Before adding annotations to any route's DTO:

1. Characterize the route's current validation behavior: what inputs are rejected, with what status code and error body.
2. Verify whether authorization/session checks occur before or after validation for that route.
3. Confirm whether the contract requires 422 (not Spring's default 400) for validation failures.
4. Map `MethodArgumentNotValidException` and `HandlerMethodValidationException` deliberately to produce the contracted error shape and status code.

Do NOT add `@Valid` indiscriminately — only on controller method parameters for HTTP request DTOs. Domain policies, post-auth validation, `EmailAddress` normalization, and business-rule checks stay as code. Annotations supplement domain validation; they do not replace it.

**What changes:** `build.gradle` gains `spring-boot-starter-validation`. HTTP DTOs gain constraint annotations. Feature exception handlers gain mapping for Bean Validation exception types to produce contracted error shapes and status codes. Mappers added where HTTP and domain models diverge.

**What does NOT change:** domain models remain independent of Jakarta annotations; missing vs. explicit `null` behavior; PATCH semantics; JSON parse failure responses; auth-before-validation ordering where contracted; error body shapes and status codes (including 422 where contracted); all SQL queries; all transaction boundaries.

**Acceptance criteria:**

- **Pending:** malformed, missing, null, unknown-field, and PATCH inputs retain the required precedence, status, and error schema per the R-00 characterization and OpenAPI contract; auth/session checks still occur before validation where contracted; HTTP DTOs use Kotlin use-site target annotations; domain models have no Jakarta annotations; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes; focused HTTP tests cover each annotated route's validation, error shape, and status code.

---

### R-05 — Extract deterministic policies

**Implements:** phase 4, second half (policy extraction). **Depends on:** R-03. **Status:** all criteria **pending**.

**Scope:** move pure decision logic from JDBC store implementations and use-case classes into `domain/policies/` per feature. Candidates include: scoring policies, assessment edit rules, draft transition rules, access-check decisions, attempt validation, and submission eligibility. Each policy is a pure Kotlin class or function with no framework, persistence, or I/O dependency.

**What changes:** deterministic decision logic moves from persistence adapters and use cases into named policy classes. Use cases call policy classes for decisions and persistence adapters for data access. No new decisions are introduced.

**What does NOT change:** SQL authorization, pagination, bounded projections, and locking remain in persistence adapters. Transaction scope, lock order, idempotency, revision checks, and rollback behavior unchanged. Policies compute results from inputs; they do not access the database, hold transactions, or make remote calls. All observable HTTP behavior unchanged.

**Acceptance criteria:**

- **Pending:** extracted policies are pure Kotlin with no Spring/JDBC/framework imports; each policy has unit tests with the same inputs/outputs as the logic it replaced; use cases call policies for decisions and ports for data; persistence adapters retain SQL authorization, pagination, and locking; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes; focused characterization tests prove unchanged behavior.

---

### R-06 — Isolate outbox

**Implements:** phase 5, outbox part. **Depends on:** R-05. **Status:** all criteria **pending**.

**Scope:** split `OutboxPublisher.kt` and `IdentityEventsTopology.kt` (already in `outbox/` feature from R-02) into individual types as specified in the source inventory:

| New type | Responsibility |
| --- | --- |
| `OutboxScheduler` | Trigger pending-event processing on schedule |
| `PublishPendingEventsUseCase` | Coordinate one batch: select, publish, mark |
| `OutboxRepository` | Outgoing port: access to outbox operational state |
| `JdbcOutboxRepository` | Persistence adapter: select with locks, updates, redrive |
| `EventPublisher` | Outgoing port: publish to broker |
| `RabbitEventPublisher` | Messaging adapter: RabbitMQ publish with confirms |
| `OutboxPayloadProtector` | Outgoing port: payload encryption/decryption contract |
| `AesGcmOutboxPayloadProtector` | Crypto adapter: AES-GCM with AAD/key-id |
| `RedriveOutboxEventUseCase` | Coordinate redrive of a failed/stuck event |
| `OutboxOperationsController` | Incoming adapter: private operational HTTP endpoints |
| `OutboxConfiguration` | Feature wiring |

Each type gets its own file. Split related request/response DTOs, exception types, and event model types into individual files as well.

**What changes:** one large file becomes multiple focused files with explicit port/adapter boundaries.

**What does NOT change:** business row and outbox row commit/rollback together; cipher format, AAD, and key-id unchanged; publisher confirms, retry count, backoff, redrive behavior, and at-least-once delivery window all unchanged; operational HTTP endpoints retain their paths, authorization, request/response shapes, and status codes; RabbitMQ topology unchanged; no credential or token leakage.

**Acceptance criteria:**

- **Pending:** each outbox type has its own file; outbox domain types have no Spring/JDBC/RabbitMQ imports; adapters implement outgoing ports; outbox commit/rollback, cipher, confirms, retry, redrive, and at-least-once behavior match R-00 characterization; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes.

---

### R-07 — Isolate shared support and cross-cutting boundaries

**Implements:** phase 5, support part. **Depends on:** R-06. **Status:** all criteria **pending**.

**Scope:** consolidate cross-feature technical capabilities under `shared/`:

```text
shared/
├── domain/
│   └── ports/out/
│       ├── TransactionRunner.kt
│       └── IdempotencyStore.kt
└── adapters/
    ├── in/web/
    │   ├── filters/
    │   └── errors/
    └── out/
        ├── transaction/
        │   └── SpringTransactionRunner.kt
        └── persistence/
            └── JdbcIdempotencyStore.kt
```

Move `OriginFilter` to its boundary: `identity/adapters/in/security/` or `shared/adapters/in/web/filters/` according to actual filter ownership determined in R-00. Move shared error-handling/exception-mapping infrastructure used across features into `shared/adapters/in/web/errors/`.

Verify `health/` feature has its complete adapter structure (controller, use case, port, probe adapter) from R-02; no additional moves expected unless R-02 left gaps.

**What changes:** `TransactionRunner`, `IdempotencyStore`, and their implementations move to `shared/`. `OriginFilter` moves to its determined boundary. Shared error handlers consolidated.

**What does NOT change:** no domain rules go into `shared/`; `shared/` has a controlled list of responsibilities (transaction running, idempotency, web filters, error mapping); health/Origin/session checks retain existing outcomes; all observable behavior unchanged.

**Acceptance criteria:**

- **Pending:** `shared/` contains only cross-feature technical capabilities, no domain rules; `OriginFilter` is outside the pure functional core; health feature has complete adapter structure; features that use `TransactionRunner` or `IdempotencyStore` depend on the shared ports, not implementations; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes.

---

### R-08 — Add springdoc-openapi

**Implements:** phase 6. **Depends on:** R-07. **Status:** all criteria **pending**.

**Scope:** add `org.springdoc:springdoc-openapi-starter-webmvc-ui` to `services/core/build.gradle`. The current Core build uses Spring Boot 4.1.2; select the exact springdoc version (3.1.x–3.2.x range) per [official compatibility guidance](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot-). Pin/resolve through Boot dependency management.

Configure in `application.properties`:

1. Opt-in docs profile (e.g., `docs`), disabled by default.
2. Default ungrouped API docs and Swagger UI disabled when the profile is inactive.
3. Public Core group at `/v3/api-docs/core-public` with the UI at `/swagger-ui.html`, enabled only in the docs profile.
4. Exclude every private, internal, outbox, and operational route from the public group.
5. Set generated docs to OpenAPI 3.1.

**What changes:** `build.gradle` gains the springdoc dependency. `application.properties` gains docs profile configuration. No new business operations, controllers, or endpoints — these are technical documentation routes only.

**What does NOT change:** normative contract at `docs/http/openapi.json` (3.1, version 1.0.8) remains unchanged and is never auto-overwritten. Swagger UI authenticates through the existing login flow and `aja_session` cookie — no auth bypass, no assumption that the UI can manually set an HttpOnly cookie. All existing HTTP behavior, SQL, transactions, and outbox semantics unchanged.

**Acceptance criteria:**

- **Pending:** with docs profile inactive: no docs JSON/YAML endpoint or Swagger UI is accessible (return 404 or equivalent). With docs profile active: `/v3/api-docs/core-public` returns OpenAPI 3.1 JSON; `/swagger-ui.html` renders the UI; compare generated public schema against normative `docs/http/openapi.json` for operation, schema, security, status, and nullability compatibility; verify every enabled docs endpoint (including ungrouped defaults if any) for private paths, schemas, operational APIs, and secret disclosure — none must appear; Swagger UI uses `aja_session` cookie authentication. `.\gradlew.bat test` passes; `.\gradlew.bat build` passes.

---

### R-09 — Harden architecture tests and run final gates

**Implements:** phase 7. **Depends on:** R-08. **Status:** all criteria **pending**.

**Scope:** update `ArchitectureTests.kt` (already moved per test inventory in R-02) with comprehensive ArchUnit rules for the final package tree. Add or verify these rules:

1. `domain` packages do not depend on Spring, Jackson, JDBC, RabbitMQ, HTTP, or adapter implementations.
2. Controllers and workers depend on incoming port interfaces, not concrete use cases.
3. Domain models do not depend on HTTP DTOs.
4. Feature `configuration/` classes concentrate implementation wiring.
5. Features do not access other features' internal implementations (adapters, use cases) — only through narrow incoming ports or explicit read projections.
6. Cross-feature dependencies follow an explicit allowed list and introduce no cycles.
7. Adapters do not depend on one another.

Keep the source-aware one-type-per-file verification (bytecode-level tools cannot establish source file ownership for Kotlin).

Run the full verification sequence:

1. `.\gradlew.bat test` — all tests pass including architecture tests.
2. `.\gradlew.bat build` — clean build succeeds.
3. Focused feature and architecture/source-shape checks.
4. `scripts/acceptance-b16.ps1` — run only when the full backlog state and environment make this gate applicable; record availability and outcome.

Review the complete authorized diff across all R-issues. Fix only confirmed findings from the review; do not introduce unrelated changes.

**What changes:** `ArchitectureTests.kt` gains comprehensive ArchUnit dependency and structure rules.

**What does NOT change:** no contract, migration, service ownership, or unrelated files changed. All behavioral invariants from R-00 characterization hold across the complete refactoring.

**Acceptance criteria:**

- **Pending:** all ArchUnit rules pass; one-type-per-file source check passes; `.\gradlew.bat test` passes; `.\gradlew.bat build` passes; `scripts/acceptance-b16.ps1` outcome recorded (pass or documented unavailability); complete diff reviewed; no contract, migration, service ownership, or unrelated file changes; exact commands, exit outcomes, service/dependency availability, operation counts, and any untested limits recorded.
