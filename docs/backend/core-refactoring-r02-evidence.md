# Core refactoring R-02 — move files into feature packages

**Issue:** R-02 (phase 2). **Status:** acceptance criteria verified; evidence and the independent review record are below.
**Plan:** [core-refactoring-plan.md](core-refactoring-plan.md). **Ownership table and approved deviations:** [core-refactoring-r00-characterization.md](core-refactoring-r00-characterization.md#declaration-to-destination-map).

This document is evidence for R-02. It does not authorize behavior changes.

## Starting point

| Item | Value |
| --- | --- |
| Branch | `codex/backend-b01-b16` |
| Base commit | `f70e382602bf4e5dc08bb676996d18cdd3a01a85` (R-01) |
| Working tree at start | clean; R-00 and R-01 present above `fa633b0` |
| Service | `services/core` (Kotlin 2.3.21, Spring Boot 4.1.2, Gradle 9.8.2, JDK 21) |
| JDK used | Azul 21.0.13 (`C:\Users\joaov\.jdks\azul-21.0.13.0.3`), set via `JAVA_HOME` |
| Docker | 29.9.3, daemon reachable (Testcontainers PostgreSQL 16 / RabbitMQ 4) |
| Secrets | none recorded; the suite uses the existing fixture values and no production credentials |

## What changed

Package declarations, import statements, and file locations only, following the declaration-to-destination map. No statement body, SQL query, transaction boundary, lock order, idempotency window, status code, or response schema changed. `services/core/build.gradle`, `services/core/src/main/resources/application.properties`, `services/core/src/main/resources/db/migration/*`, and `docs/http/openapi.json` were not edited.

### Main sources

179 files were relocated with unchanged filenames (166 main, 13 test). Every type now lives under `<feature>/{domain,adapters}` per the ownership table; nothing remains in the old flat `application/`, `ports/`, `adapter/`, or `domain/` packages.

| Old location | New location |
| --- | --- |
| `application/academic/Manage*.kt` | `classroom`/`curriculum`/`forum`/`attendance`/`announcements`/`dashboard`/`materials`/`assessment` `domain/usecases` |
| `ports/out/*.kt` | `<feature>/domain/{models,ports/out}`; `IdempotencyStore`/`IdempotencyKeyReuse` → `shared/domain/ports/out` |
| `adapter/out/persistence/Jdbc*.kt` | `<feature>/adapters/out/persistence`; `JdbcIdempotencyStore` → `shared/adapters/out/persistence` |
| `adapter/out/questions/*.kt` | `assessment/adapters/out/questions` |
| `adapter/out/messaging/*.kt` | `outbox/adapters/out/messaging` and `outbox/domain/models` |
| `adapter/out/security/Argon2PasswordHasher.kt` | `identity/adapters/out/security` |
| `domain/{identity,health}/*.kt` | `identity/domain/{models,policies}`, `health/domain/models` |
| root `*Controller.kt`, `*Request.kt`, `*ExceptionHandler.kt` | `<feature>/adapters/in/controllers` (+ `models/requests`, `models/responses`, `handlers`) |
| `OriginFilter.kt`, `FileUploadSessionFilter.kt` | `shared/adapters/in/web/filters`, `materials/adapters/in/filters` |

Composite files split by this issue (approved before implementation — "split now; shared helpers in `shared/`"):

- `AcademicController.kt` (25 top-level types, 48 mappings) → `ClassroomController`, `CurriculumController`, `ForumController`, `AttendanceController` (48 mappings total) and their request DTOs.
- `AcademicExceptionHandler.kt` (14 `@ExceptionHandler` methods) → per-feature handlers `ClassroomExceptionHandler`, `CurriculumExceptionHandler`, `ForumExceptionHandler`, `AttendanceExceptionHandler`, `AssessmentExceptionHandler`, plus the cross-feature fallback in `SharedExceptionHandler` (14 `@ExceptionHandler` methods total).

8 shared HTTP-edge files live under `shared/adapters/in/web/{requests,errors}` (6 newly created helpers/handlers — `requestId`, `requestRevision`, `requestIdempotencyKey`, `requestText`, `errorBody`, `SharedExceptionHandler` — plus 2 relocated from root — `OrderCommandRequest`, `OrderItemRequest`) for the helpers that are genuinely cross-feature and free of feature-package dependencies. Feature-scoped helpers (`actor` in `Classroom`/`Curriculum`/`Forum`/`AttendanceController`, `pagedBody` in `Classroom`/`Curriculum`/`AttendanceController`) and single-caller helpers (`requestInteger` in `ClassroomController`, `requestStrings` in `CurriculumController`) live privately in their calling controllers, and `shared/` has zero imports from any other feature (`classroom`, `identity`, etc.). Only identical helpers were shared; the academic `text()` (uses `asText()`) and the content `text()` (uses `asString()`) were not merged.

`CoreApplication.kt` stays in the root `br.com.ajadigital.core` package: relocating it to a sibling `configuration` subpackage would change `@SpringBootApplication`'s default component-scan base package (breaking discovery of every feature) **and** break `@SpringBootTest` upward `@SpringBootConfiguration` discovery from `CoreIntegrationTestBase` (`br.com.ajadigital.core`, no explicit `classes = [...]`) and every per-feature `*IntegrationTests` subclass. Moving bean wiring into feature `configuration/` classes is R-03 work.

### Tests

13 test files were relocated; the test file count is unchanged at 23 (10 remain at root `br.com.ajadigital.core`: 4 edited in place, 6 untouched including `PlatformIntegrationTests.kt` and the shared test configurations/base).

- Per-feature integration tests moved into their feature package (`curriculum/CurriculumIntegrationTests`, etc.); `HealthControllerTests` → `health/adapters/in/controllers`, `OriginFilterTests` → `shared/adapters/in/web/filters`, `SessionCookieTests` → `identity/adapters/in/controllers`, `HttpQuestionSnapshotProviderTests` → `assessment/adapters/out/questions`.
- `CoreIntegrationTestBase.featureClassCount()` was generalized from a single-directory listing to a recursive walk of the test-classes root, so the aggregate OpenAPI coverage gate still counts all 10 concrete `CoreIntegrationTestBase` subclasses now that they live in different packages. The gate logic and the `B16_OPENAPI_COVERAGE=true` condition are unchanged.
- `ArchitectureTests.kt` was updated for the new tree (3 tests): feature `domain/` packages must not import `org.springframework`, `javax.sql`, `com.rabbitmq`, or `jakarta.`; `*Controller.kt` files must not touch `javax.sql.DataSource` or `ConnectionFactory`; `health/domain/ports/out/HealthProbe.kt` must exist. Hardened ArchUnit rules remain R-09.

### Mechanical corrections the move required

Three edits beyond the mechanical package/import rewrites were necessary to keep behavior identical.

1. **`adapters/in` is a Kotlin hard keyword.** `in` cannot appear as a plain package segment, so the package segments are escaped as `` adapters.`in`.… ``. Directory names stay `in`.
2. **Error-mapping precedence.** See below — this was a real regression the move introduced, and the fix restores R-01 behavior rather than changing it.
3. **Unused imports pruned.** Compared directly against base commit `f70e382`, 15 pre-existing unused import lines were removed across 11 files (`JdbcAcademicStore` 1, `JdbcAnnouncementStore` 3, `JdbcContentStore` 1, `AssessmentIntegrationTests` 2, `AttendanceIntegrationTests` 1, `CoreIntegrationTestBase` 1, `CoreOpenApiCoverageFilter` 2, `CurriculumIntegrationTests` 1, `ForumIntegrationTests` 1, `IdentityIntegrationTests` 1 [`java.nio.file.Path`], `OutboxIntegrationTests` 1). An intermediate import-pruner pass misparsed `Regex(""""token":"([^"]+)"""")` in `IdentityIntegrationTests.kt` and stripped 8 genuinely required imports (`patch`, `get`, `Cookie`, `DriverManager`, `Executors`, `TimeUnit`, `Duration`, `MessageDigest`), causing `:compileTestKotlin` to fail; restoring those 8 imports fixed compilation while keeping truly unused `java.nio.file.Path` removed. Removing an unused import cannot change compiled bytecode.

#### Error-mapping regression and fix

`AcademicExceptionHandler` and `AuthExceptionHandler` both mapped overlapping exception types: `DuplicateKeyException` (Auth → `409 VALIDATION_ERROR`) is a subtype of `DataIntegrityViolationException` (Academic → the generic fallback `409 BUSINESS_CONFLICT`), which is a subtype of `DataAccessException` (Auth → `500 PERSISTENCE_FAILED`). Spring sorts `@ControllerAdvice` beans with a **stable** `OrderComparator.sort` (`ControllerAdviceBean.findAnnotatedBeans`); with no `@Order`, ties keep bean-registration order.

Before R-02 the two advices sat in the same package and `AcademicExceptionHandler` happened to be registered first, so the academic fallback won and duplicate/structure conflicts resolved to `409 BUSINESS_CONFLICT` (the contract: `docs/http/contract.md` — exclusivity/structure invariants in administration are `BUSINESS_CONFLICT`). After the split, `SharedExceptionHandler` (the academic fallback's successor) is registered **after** `AuthExceptionHandler`, so overlapping integrity exceptions resolved to Auth's handlers instead:

- Verified runtime reproduction: `CurriculumIntegrationTests.academicMutationsWriteMinimalAuditAndIdempotentReplayDoesNotDuplicateIt` triggers `DuplicateKeyException` on duplicate class code (`core.classes.normalized_code` UNIQUE) and received `409 VALIDATION_ERROR` instead of `409 BUSINESS_CONFLICT` (whereas the detached worktree at `f70e382` passed). (By contrast, `concurrentAdminDeactivationCannotRemoveTheLastAdministrator` triggers `IllegalStateException("last administrator")` in `JdbcAcademicStore.protectLastAdmin`, which `SharedExceptionHandler` handles without `AuthExceptionHandler` overlap.)
- Static subtype consequence: because `DataIntegrityViolationException` extends `DataAccessException`, any non-`DuplicateKeyException` integrity violation (for example a foreign-key or check constraint failure) would resolve to `AuthExceptionHandler`'s `DataAccessException` handler (`500 PERSISTENCE_FAILED`) instead of `409 BUSINESS_CONFLICT` without `@Order(Ordered.HIGHEST_PRECEDENCE)`.

Fix: `@Order(Ordered.HIGHEST_PRECEDENCE)` on `SharedExceptionHandler`, restoring the R-01 precedence (the academic/business fallback is consulted before `AuthExceptionHandler`). This is the only change to error mapping; `AuthExceptionHandler.kt` body is unchanged and no handler method, status code, or error code string changed.

## Verification

### Mechanical checks

| Check | Result |
| --- | --- |
| Main `.kt` files at base commit (`f70e382`) / now | `169` / `182` |
| Test `.kt` files at base commit (`f70e382`) / now | `23` / `23` |
| Main file disposition vs `f70e382` | `166` relocated, `15` new (split/shared), `2` split-deleted (`AcademicController.kt`, `AcademicExceptionHandler.kt`), `1` kept in place (`CoreApplication.kt`); `169 - 2 + 15 = 182` |
| Test file disposition vs `f70e382` | `13` relocated, `0` added, `0` deleted; `10` kept at root (`4` edited in place, `6` untouched) |
| One-top-level-type-per-file & package-path parity | `PASS` — all `182` main and `23` test `.kt` files declare the exact package matching their path (with backticked `` `in` ``) and a single matching top-level type/function (with `CoreApplication.kt` + `main` and `IdentityEventsTopology.kt` + `EVENTS_EXCHANGE` as documented in R-00/R-01) |
| `shared/` dependency check | `0` imports or qualified references from `shared/` (`12` files total: `8` under `shared/adapters/in/web/{requests,errors}`, `OriginFilter.kt`, `IdempotencyStore.kt`, `IdempotencyKeyReuse.kt`, `JdbcIdempotencyStore.kt`) to any other feature package |
| Non-`.kt` files changed | `0` (`docs/backend/core-refactoring-r02-evidence.md` added) |
| Files edited in place (not moved) | `5`: `CoreApplication.kt`, `ArchitectureTests.kt`, `CoreIntegrationTestBase.kt`, `CoreOpenApiCoverageFilter.kt`, `TestQuestionSnapshotProvider.kt` |
| Pre-existing unused import lines removed vs `f70e382` | `15` across `11` files (`5` in 3 main files, `10` in 8 test files) |
| Old flat-package references remaining (`application`/`ports`/`adapter`/`domain`) | `0` |
| Empty packages / directories under main or test | `0` |
| Feature packages | `12` (`announcements`, `assessment`, `attendance`, `classroom`, `curriculum`, `dashboard`, `forum`, `health`, `identity`, `materials`, `outbox`, `shared`), each with `adapters/` and `domain/` |
| `AcademicController` / `AcademicExceptionHandler` split parity | `48` route mappings -> `48` across 4 feature controllers; `14` `@ExceptionHandler` methods -> `14` across 5 feature handlers + `SharedExceptionHandler` |
| `services/core/build.gradle`, `application.properties`, `db/migration/*`, `docs/http/openapi.json` diffs | empty (zero diff) |
| SQL string literals (main + test) | `650` leading-DML/DDL literals (`498` distinct) and `680` SQL-fragment literals (`516` distinct) at both `f70e382` and working tree; **zero** multiset differences |
| Non-SQL body deltas vs `f70e382` | Across all `167` common main files, only fully-qualified package-name rewrites in `12` files (`AuthExceptionHandler.kt` body unchanged); across all `23` test files, only `ArchitectureTests.kt`, `CoreIntegrationTestBase.featureClassCount()`, and `1` qualified exception reference in `TestQuestionSnapshotProvider.kt` |

### Commands

Run from `services/core` with `JAVA_HOME` set to `C:\Users\joaov\.jdks\azul-21.0.13.0.3` **after the final `.kt` edits** (review fixes inlining `actor`, `pagedBody`, `requestInteger`, `requestStrings` and deleting those 4 `shared/` files).

| # | Command | Exit | Outcome |
| --- | --- | --- | --- |
| 1 | `.\gradlew.bat test --rerun-tasks --console=plain` (`B16_OPENAPI_COVERAGE` unset) | `0` | `BUILD SUCCESSFUL in 10m 1s`; `7 actionable tasks: 7 executed` (`:processResources`, `:compileKotlin`, `:resolveMainClassName`, `:bootJar`, `:jar`, `:compileTestKotlin`, `:test`); JUnit XML total: `124` tests, `0` failures, `0` errors, `0` skipped across all 15 test classes (`ArchitectureTests` 3, `AnnouncementsIntegrationTests` 6, `AssessmentIntegrationTests` 23, `HttpQuestionSnapshotProviderTests` 1, `AttendanceIntegrationTests` 4, `CurriculumIntegrationTests` 18, `DashboardIntegrationTests` 7, `ForumIntegrationTests` 16, `HealthControllerTests` 1, `IdentityIntegrationTests` 20, `SessionCookieTests` 1, `MaterialsIntegrationTests` 8, `OutboxIntegrationTests` 4, `PlatformIntegrationTests` 5, `OriginFilterTests` 7). |
| 2 | `.\gradlew.bat build --rerun-tasks --console=plain` with process-scoped `$env:B16_OPENAPI_COVERAGE = 'true'` | `0` | `BUILD SUCCESSFUL in 9m 5s`; `7 actionable tasks: 7 executed` (`:processResources`, `:compileKotlin`, `:resolveMainClassName`, `:bootJar`, `:jar`, `:compileTestKotlin`, `:test`; lifecycle `:classes`, `:assemble`, `:check`, `:build` completed); JUnit XML total: `124` tests, `0` failures, `0` errors, `0` skipped; `featureClassCount() == 10` compiled `*IntegrationTests` classes triggered `CoreOpenApiCoverageFilter.assertEveryOperationHasSuccessAndExpectedDenial()`, confirming all `95` non-`/questions` operations in `docs/http/openapi.json` had both 2xx success and expected denial coverage (with `B16_OPENAPI_COVERAGE` cleared after the command). |

### Non-changes

- No HTTP operation, path, status code, response schema, cookie, Origin rule, or idempotency behavior changed; `docs/http/openapi.json` is untouched.
- No SQL, migration, `build.gradle`, or `application.properties` change; no new dependency.
- No new production feature or endpoint; no use-case extraction (that is R-03/R-05).

## Known limits

- **Outbox & crypto adapter coupling (recorded deviations from the R-02 "no adapter-to-adapter dependency" criterion, deferred to R-06).**
  1. `OutboxEncryption` is placed in `identity/adapters/out/security` per the plan's source inventory ("policy/crypto adapter types") and R-00's crypto-adapter classification. Because the R-06 `OutboxPayloadProtector` port does not exist yet, this leaves four cross-layer edges: `ManageAcademics`, `RegisterStudent`, `RequestAuthToken` (`domain` → the crypto adapter) and `JdbcAnnouncementStore` (`adapters` → the crypto adapter). Extracting the port is R-06's authorized scope (plan: "`AesGcmOutboxPayloadProtector` behind `OutboxPayloadProtector` port"); creating it in R-02 would be a "for the future" abstraction the plan forbids.
  2. `OutboxOperationsController` (`outbox/adapters/in/controllers`) depends directly on `OutboxRedrive` (`outbox/adapters/out/messaging`, which injects `JdbcTemplate`). Separating `RedriveOutboxEventUseCase`, `OutboxRepository`, and `JdbcOutboxRepository` is explicitly R-06 scope (`core-refactoring-plan.md`); creating those types in R-02 would anticipate R-06.
- **Pre-existing `AuthExceptionHandler.concurrentDuplicate()` shadowing (unchanged from R-00/R-01).** `AuthExceptionHandler` was untouched in R-02; its `@ExceptionHandler(DuplicateKeyException::class)` handler remains shadowed by `SharedExceptionHandler`'s `@Order(Ordered.HIGHEST_PRECEDENCE)` `DataIntegrityViolationException` handler identically to how `AcademicExceptionHandler` shadowed it in R-00/R-01. Consolidating or scoping overlapping error handlers belongs to R-07 or a separate behavior issue.
- Cross-feature HTTP-edge helpers in `shared/adapters/in/web/{requests,errors}` are shared within the same inbound web layer (`adapters.in`) as approved for the `AcademicController`/`AcademicExceptionHandler` split and have zero dependencies on any feature package.
- The aggregate coverage gate still no-ops on a filtered or partial run (documented R-01 limitation, unchanged); the recursive `featureClassCount()` only affects which run performs the assertion.
- The suite was executed on this machine only (Docker Desktop, PostgreSQL 16 and RabbitMQ 4 containers), not in CI.

## Review record

Independent read-only reviews were run against the complete R-02 diff and acceptance evidence against base commit `f70e382`, with targeted rechecks by the same three reviewers after confirmed fixes and full post-edit Gradle validation.

| Reviewer | Verdict | Findings and disposition |
| --- | --- | --- |
| Database reviewer (`.codex/database-reviewer.toml`) | **APPROVE** | 0 code/SQL/database defects (722 leading DML/DDL SQL literals verified identical; all 48 mappings and 27 `@Transactional` annotations preserved; zero migration/config changes). Round 1 Low #1 (exception attribution in lines 71–74 conflating `concurrentAdminDeactivationCannotRemoveTheLastAdministrator` `IllegalStateException` with `DataIntegrityViolationException`) — **fixed** and verified in targeted recheck. Pre-existing `AuthExceptionHandler.concurrentDuplicate()` shadowing noted as identical to R-00/R-01. |
| ECC code reviewer (`docs/agents/code-reviewer.md`) | **APPROVE** | 0 CRITICAL/HIGH findings. Round 1 MEDIUM (`shared/adapters/in/web/requests/{actor,pagedBody}.kt` created `classroom <-> shared` cycle) — **fixed** (4 files deleted from `shared/`, private helpers inlined in calling controllers, `shared/` verified at 0 feature imports) and verified in targeted recheck. Round 1 LOW (`CoreApplication.kt` doc alignment + unstaged edits in `IdentityIntegrationTests.kt` and evidence doc) — **fixed** and verified cleanly staged. |
| Ponytail reviewer (`.agents/skills/ponytail-review/SKILL.md`) | **Ship** | Round 1 findings 1 (`actor.kt`/`pagedBody.kt` cycle), 2 (single-caller `requestInteger.kt`/`requestStrings.kt` in `shared/`), 4 (evidence doc counts/exception text), and 5 (stale staged index for `IdentityIntegrationTests.kt`) — **fixed** and verified in targeted recheck. Round 1 finding 3 (`AuthExceptionHandler.concurrentDuplicate()`) — **dismissal accepted** for R-02 scope (`AuthExceptionHandler.kt` body untouched from `f70e382`, runtime shadowing matches R-00/R-01, documented in `## Known limits` for R-07 or a dedicated behavior issue). Nice-to-have #6 (minor duplicate `error()`/`id()`/`revision()` helpers in `AuthController`, `AuthExceptionHandler`, `HealthController`, `AnnouncementController`) deferred to R-07. |
