# Core refactoring R-01 — one type per file

**Issue:** R-01 (phase 1). **Status:** acceptance criteria verified. Evidence and the independent review record are below.
**Plan:** [core-refactoring-plan.md](core-refactoring-plan.md). **Characterization and approved deviations:** [core-refactoring-r00-characterization.md](core-refactoring-r00-characterization.md#decisions-approved-for-r-01).

This document is evidence for R-01. It does not authorize behavior changes.

## Starting point

| Item | Value |
| --- | --- |
| Branch | `codex/backend-b01-b16` |
| Base commit | `1056c2015276ac21d08027607c71bf113f2ba660` (R-00) |
| Working tree at start | clean; only the R-00 commit is present above the base branch |
| Service | `services/core` (Kotlin 2.3.21, Spring Boot 4.1.2, Gradle 9.8.2, JDK 21) |
| JDK used | Azul 21.0.13 (`%USERPROFILE%\.jdks\azul-21.0.13.0.3`), set via `JAVA_HOME` |
| Docker | 29.9.3, daemon reachable (Testcontainers PostgreSQL/RabbitMQ) |
| Secrets | none recorded; the suite uses the existing fixture values and no production credentials |

## What changed

Only file splits, the four deviations approved for R-01, and the test-support reorganization those require.

### Main sources

26 files at the base commit declared more than one top-level type. They held 138 top-level types, which now live in 138 one-type files, plus 3 files for the top-level functions that shared those files (`SessionRecord.toResponse`, `createSessionCookie`, `AuthTokenResult.toUserOrThrow`). No package, type name, member signature, or statement body changed; only the seven approved declarations below widened their visibility. `build.gradle`, `application.properties`, migrations, and SQL were not edited.

| Base file | Top-level types |
| --- | --- |
| `AcademicController.kt` | 25 |
| `AnnouncementController.kt` | 4 |
| `AssessmentController.kt` | 3 |
| `AuthController.kt` | 13 |
| `ContentController.kt` | 6 |
| `adapter/out/messaging/OutboxPublisher.kt` | 5 |
| `adapter/out/questions/HttpQuestionSnapshotProvider.kt` | 5 |
| `application/academic/ManageAcademics.kt` | 6 |
| `application/academic/ManageAssessments.kt` | 3 |
| `application/academic/ManageAttempts.kt` | 2 |
| `application/academic/ManageAttendance.kt` | 2 |
| `application/academic/ManageSubjects.kt` | 3 |
| `application/academic/ManageSubmissions.kt` | 2 |
| `application/academic/ManageTopics.kt` | 2 |
| `application/identity/AuthenticateStudent.kt` | 6 |
| `application/identity/ManageAuthTokens.kt` | 7 |
| `application/identity/RegisterStudent.kt` | 6 |
| `application/identity/UpdateOwnProfile.kt` | 3 |
| `ports/out/AcademicStore.kt` | 3 |
| `ports/out/AnnouncementStore.kt` | 3 |
| `ports/out/AssessmentStore.kt` | 11 |
| `ports/out/AttendanceStore.kt` | 2 |
| `ports/out/ContentStore.kt` | 4 |
| `ports/out/IdempotencyStore.kt` | 2 |
| `ports/out/IdentityStore.kt` | 8 |
| `ports/out/TopicStore.kt` | 2 |

`ManageAuthTokens.kt` declared no type of its own and was deleted after its seven types and one function were placed in their own files. All other 25 base paths remain and now hold exactly one type.

**Approved deviation 4** — top-level `private` declarations that became cross-file were widened to `internal`, and only these: `UnroutableOutboxEvent`, `PendingOutboxEvent`, `SnapshotRequest`, `SnapshotResponse`, `SnapshotDto`, `ImageDto`, `AuthTokenResult.toUserOrThrow`.

### Tests

`CoreApplicationTests.kt` (5,721 lines) declared seven top-level support types plus the suite class. It is now:

- seven single-type support files: `MutableTestClock`, `MutableTestClockConfiguration`, `EmailQueueTopologyTestConfiguration`, `TestQuestionSnapshotProvider`, `AssessmentSnapshotTestConfiguration`, `CoreOpenApiCoverageFilter`, `CoreOpenApiCoverageConfiguration`;
- `CoreIntegrationTestBase.kt` — the shared `@SpringBootTest` base class (**approved deviation 3**), holding the injected collaborators, the 37 helper members, the nested `CoreChild`/`QuestionsChild` records, the Testcontainers bootstrap, and the one aggregate coverage hook;
- ten per-feature classes (**approved deviation 1**) that contain only the original test methods.

| Feature class | Tests |
| --- | --- |
| `DashboardIntegrationTests` | 7 |
| `AnnouncementsIntegrationTests` | 6 |
| `ForumIntegrationTests` | 16 |
| `AssessmentIntegrationTests` | 23 |
| `MaterialsIntegrationTests` | 8 |
| `CurriculumIntegrationTests` | 18 |
| `IdentityIntegrationTests` | 20 |
| `OutboxIntegrationTests` | 4 |
| `PlatformIntegrationTests` | 5 |
| `AttendanceIntegrationTests` | 4 |
| **Total** | **111** |

Test lifecycle changes, all required for the split:

- `@DirtiesContext(AFTER_CLASS)`, `@Testcontainers`, and `@Container` were removed so the ten classes share one Spring context and one pair of containers; the container bootstrap DDL is guarded by a static `AtomicBoolean` so it runs once per JVM. The DDL statements themselves are unchanged. This is entailed by deviations 1 and 3 — ten subclasses cannot each own a container — and the database review confirmed the assert semantics of the affected tests do not depend on per-class teardown. `@TestInstance(PER_CLASS)` is declared once on the base and inherited; the subclasses do not repeat it.
- Constructor injection became `@Autowired protected lateinit var` field injection so subclasses can extend the base.
- Helpers and the nested `CoreChild`/`QuestionsChild` types widened from `private` to `protected` in the base class.
- Each split file's import list was pruned to the imports it uses (no wildcard or aliased imports exist): main import lines 3,044 → 711 and test import lines 1,694 → 265, against 577 and 93 at the base commit.
- **Approved deviation 2** — OpenAPI coverage state (the parsed operation set and the observed responses) moved to the filter's companion object, and the assertion now runs once per run: each feature class's inherited `@AfterAll` decrements a shared counter seeded from the number of concrete `CoreIntegrationTestBase` subclasses in the class-output directory, and the last one asserts. The gate is still `B16_OPENAPI_COVERAGE=true`; the operation set still excludes `/questions` (95 operations). This design assumes one test JVM for the whole suite; see §Known limits.

## Verification

### Mechanical checks

| Check | Result |
| --- | --- |
| Base-commit main `.kt` files | 54 |
| Main `.kt` files after split | 169 |
| Main files with more than one top-level type | 0 |
| Main single-type files whose name differs from their type | 0 |
| Test `.kt` files after split | 23 (6 at the base commit, 1 deleted, 18 added) |
| Test files with more than one top-level type | 0 |
| Test single-type files whose name differs from their type | 0 |
| Independent verbatim check of the split against the base commit | `PASS: 26 split files verified` — every declaration is a verbatim slice of its original file, modulo the seven approved `private` → `internal` widenings; no original line vanished |
| Independent verbatim check of the test split against the base commit | `PASS: 111 tests identical, 37 helpers identical, 7 coverage members rewritten under approved deviation 2` — tests compared annotation set and body text after dedenting; only the coverage filter members and the aggregate `@AfterAll` hook differ |

### Commands

Run from `services/core` with `JAVA_HOME` set to the Azul 21 JDK.

| # | Command | Exit | Outcome |
| --- | --- | --- | --- |
| 1 | `.\gradlew.bat test --rerun-tasks --console=plain` with `B16_OPENAPI_COVERAGE=true` | 0 | `BUILD SUCCESSFUL in 5m 52s`; the aggregate gate ran on the final source and found every one of the 95 non-Questions operations covered. |
| 2 | `.\gradlew.bat build --rerun-tasks --console=plain` | 0 | `BUILD SUCCESSFUL in 6m 26s`; main and test compilation, assembly, and the full suite: 122 tests, 0 failures, 0 skipped. Final acceptance run. |
| 3 | `.\gradlew.bat test --tests "*DashboardIntegrationTests" --console=plain` with `B16_OPENAPI_COVERAGE=true` and the temporary `R01_COVERAGE_PROBE=true` | 1 | Negative probe: a temporary `if (System.getenv("R01_COVERAGE_PROBE") == "true") return 1` in `featureClassCount()` forced the counter to complete after the single filtered class, and the run failed at `CoreOpenApiCoverageFilter.kt:206` with `OpenAPI operations without a successful HTTP request: … ==> expected: <true> but was: <false>`. The temporary line was removed afterwards; a repository search for `R01_COVERAGE_PROBE` returns nothing. Without that line a filtered run skips the assertion (see §Known limits). |

Earlier runs during the issue (`test` 6m 01s, coverage-gated `test` 7m 27s, `build` 6m 31s) preceded only whitespace and unused-import cleanup; rows 1 and 2 are the acceptance runs on the committed state. Row 3 ran before that cleanup; the counter and assertion code it exercises is unchanged, and row 1 re-exercises the same path afterwards.

One earlier coverage run ended with `Gradle build daemon disappeared unexpectedly` before the test task completed; the same command was rerun (row 2) with no source change and passed. No test failed in the crashed attempt.

### Non-changes

- No HTTP operation, status code, schema, cookie, Origin, or idempotency behavior changed; the OpenAPI document is untouched.
- No SQL, migration, `build.gradle`, or `application.properties` change.
- No package move (that is R-02), no new dependency, no new production type.

## Known limits

- The aggregate coverage assertion is gated on all ten feature classes running in one JVM, which is how `scripts/acceptance-b16.ps1` invokes the suite (no `forkEvery`/`maxParallelForks`, no JUnit parallelism). **A filtered or partial Gradle run (`--tests`, a `@Disabled` class, or a future fork/parallel test configuration) does not reach the assertion and stays green**, so the gate can no longer detect that coverage was incomplete; conversely, if the class count is under-estimated the assertion fires early and fails. The class count comes from the compiled test classes, not from the JUnit test plan. The approved deviation chose shared state plus one assertion per run; a fully filter-proof mechanism needs `org.junit.platform.launcher` on the test compile classpath (it is `testRuntimeOnly` today — a `build.gradle` change) or a sentinel the acceptance script verifies (a script change). Neither is in R-01 scope; this is recorded as a follow-up.
- Test execution order across the ten classes is not fixed; the shared context and containers make the suite order-independent in this run, but the suite was not exercised with parallel test execution.
- The suite was executed on this machine only (Docker Desktop, PostgreSQL 16 and RabbitMQ 4 containers), not in CI.

## Review record

Independent read-only reviews were run against the complete uncommitted R-01 diff and its acceptance evidence. All three reviewers inspected the working tree at base commit `1056c2015276ac21d08027607c71bf113f2ba660`; the executor re-ran the affected checks after any fix.

| Reviewer | Verdict | Findings and disposition |
| --- | --- | --- |
| Database reviewer (PostgreSQL, `.codex/database-reviewer.toml`) | **APPROVE** | No database-impacting finding. Bootstrap DDL is character-identical to the base commit modulo indentation inside the new one-time guard; no production SQL, migration, schema, role, or privilege change; the guard is correct for the confirmed single-JVM, single-context topology. Non-blocking notes: (H1) `postgres.start()`/`rabbit.start()` stay outside the guard — dismissed, Testcontainers `start()` is idempotent; (H2) class ordering is no longer fixed inside one class — accepted risk, the full suite ran green and state assertions are delta-based; (H3) unused imports remain in the generated files — intentional, the split keeps each original header verbatim and the plan forbids import changes beyond updated references. |
| ECC code reviewer (`docs/agents/code-reviewer.md`) | **APPROVE** | No CRITICAL/HIGH finding; all four acceptance criteria verified at source level (26 multi-type files → 169 one-type files; 111 tests and 28 helper functions preserved; 7 widenings only; no SQL/migration/build/properties/contract change). F1 (MEDIUM): the aggregate gate no-ops on filtered/partial/forked runs — accepted as a documented limitation (§Known limits) because the listener fix needs a `build.gradle` change (`org.junit.platform.launcher` is `testRuntimeOnly`) or a script change, neither in R-01 scope; the acceptance script path is unaffected. F2 (LOW): container/`@DirtiesContext` annotation removal was not in the four recorded deviations — recorded above as entailed by deviations 1 and 3, with the database review confirming no semantic change, rather than rewriting the R-00 approval record. F3 (LOW): monolith-wide import blocks — **fixed** (3,770 unused import lines removed). |
| Ponytail reviewer (`.agents/skills/ponytail-review/SKILL.md`) | **APPROVE** | No must-fix finding; test and main splits confirmed faithful by token parity (111 tests, 166 type names, identical HTTP/SQL/`require`/`throw`/`@Transactional` counts). Finding 1 (import bloat, ~4,000 lines) — **fixed**. Finding 2 (gate can pass under a future fork/parallel configuration) — same as ECC F1; dismissed for R-01 for the same reason and recorded as a follow-up. Finding 3 (ten redundant `@TestInstance` lines) — **fixed**; the base declares it and subclasses inherit it. Finding 4 (no permanent test for the gate's fail path) — deferred: R-01 keeps every existing test and adds no new test type; the negative probe is recorded above. |