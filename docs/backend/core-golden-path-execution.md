# Core golden path execution record

## GP-00 baseline

**GP-00 status: complete.** Focused independent ECC review and recheck: **APPROVE**, zero outstanding findings.

| Item | Current evidence |
| --- | --- |
| Date / revision / branch | 2026-11-17; `d10642107e86464631584d7c2f4424eac40a910b`; `codex/backend-b01-b16`. |
| Git state | 870 changed/untracked paths reported by the coordinator; 475 untracked; 846 under `services/core/src/main`, 17 under Core tests; no staged changes. The worktree includes broad pre-existing changes. Do not attribute them to GP-00 or commit them here. |
| Relevant pre-existing changes | `ARCHITECTURE.md`, `docs/backend/core-refactoring-plan.md`, and broad Core source/test/build work appear related to golden path/R-09 planning or implementation. The assigned document diffs were inspected; the full Core diff has not been reviewed, so those source/build changes remain not-understood for acceptance and must be audited issue-by-issue. |
| Unrelated pre-existing changes | `.gitignore` and `AGENTS.md` are outside GP-00's authorized scope and were not changed by this issue. Other top-level/config/skills changes have not been inspected enough to classify individually; treat as not-understood, preserve them. |
| Graphify | `graphify-out/graph.json` exists. Queries were run before source navigation; returned R-00–R-06 nodes/declarations that predate much of the current worktree. Mark graph stale; never use it as current dependency proof. Refresh is required only after a code issue changes sources. |
| Current toolchain | System `java`, Gradle without explicit JAVA_HOME, and Docker commands are absent from PATH. Explicit `C:\Users\joaov\.jdks\azul-21.0.13.0.3\bin\java.exe` is available; with `JAVA_HOME=C:\Users\joaov\.jdks\azul-21.0.13.0.3`, `services/core/gradlew.bat --version` succeeds: Gradle 9.8.2, Kotlin 2.4.10, Windows 11. Compile/unit tasks are available. Docker/Podman/Nerdctl and PostgreSQL/RabbitMQ CLI/process availability have not been established; do not claim Testcontainers infrastructure is usable. |
| Historical evidence | R-00–R-06 documents record historical clean/verified states, including tests/builds with Docker/Testcontainers at their own revisions. Those results do not validate the current worktree or replace current affected checks. |
| Contracts read | Orchestration prompt, `AGENTS.md`, `ARQUITETURA.md`, `ARCHITECTURE.md`, operation ownership, normative OpenAPI, outbox operations, core plan and R-00–R-06 evidence. |

### Change classification

- **Relevant but not accepted as verified:** architectural/plan docs and broad Core source/test/build modifications that align by path/topic with the golden path. Full source diff, callers, and behavior have not been reviewed; GP-01/02 must establish ownership and evidence before relying on them.
- **Unrelated to GP-00:** existing edits to `.gitignore` and `AGENTS.md`; preserved untouched.
- **Not understood:** uninspected changed/untracked configuration, skills, docs, and source details. Preserve them and classify under their authorized issue only after targeted inspection.

These categories describe topic and review state, not authorship. No baseline file was reverted, staged, or committed.

## Acceptance checklist

| Criterion | Status | Evidence |
| --- | --- | --- |
| Starting revision, branch, staged/unstaged/untracked state recorded | verified | Table above and coordinator `git status` evidence. |
| Graphify query precedes source navigation; freshness limitation explicit | verified | Query executed; graph returned prior R-00–R-06 declarations and is stale against current changes. |
| Existing changes classified without destructive cleanup | verified | Classification above; no reset/stash/discard. |
| D-01–D-12 authority and distinctions synchronized | verified | [Local issues](core-golden-path-issues.md), [ARCHITECTURE.md](../../ARCHITECTURE.md), and plan header. |
| GP-00–GP-07 and GP-04 flow subissues ordered with dependencies and per-criterion statuses | verified | [Local issue backlog](core-golden-path-issues.md). |
| Ownership, exports, actual allowed dependency edges, data dependencies, and transaction coordinators documented | verified | The backlog records a query-backed cross-owner baseline with source columns where explicit, all persisted columns where SQL uses `*`, current data owner/consumer, authorization scope, and affected tests. It is not an exhaustive inventory of every Core query; each flow must revalidate its complete queries and row mappings before edits. |
| Documentation links and claims checked; no code/service checks claimed | verified | Relative Markdown targets and local heading anchors pass for all four updated documents; `git diff --check` passes; mojibake/replacement scan is clean. No service/runtime check is claimed. |
| Current dirty-tree forced compilation | verified | Explicit JDK `JAVA_HOME`; `compileKotlin compileTestKotlin --rerun-tasks --quiet --console=plain` exit 0. Compilation only, no tests/runtime/infra proof. |
| GP-01 implementation readiness at GP-00 checkpoint | blocked at that checkpoint | Required HTTP/PostgreSQL services were unavailable or unproven then, so GP-01 stayed held. Readiness was rechecked for GP-01; see the later GP-01 evidence and status below. |
| Focused independent ECC contract-consistency/links review | verified | Final recheck: APPROVE, zero outstanding findings. The two initial findings and their fixes are recorded below. GP-00 uses ECC review only; no Ponytail or database review applies. |

### ECC findings and recheck

| Finding | Resolution | Recheck |
| --- | --- | --- |
| Align `ARCHITECTURE.md` file organization with D-09's one-primary-declaration convention and related subtype/helper allowance. | Updated the convention and PSI verification guidance; no mandatory split for every private helper or subtype remains. | Approved; no outstanding finding. |
| Hold GP-01 implementation because required HTTP/PostgreSQL acceptance services are unavailable, and make GP-02 next eligible. | Backlog now blocks GP-01 production/test changes and limits interim work to read-only diagnosis/readiness. Execution next action is GP-02 preflight after GP-00 closure. | Approved; no outstanding finding. |

## Commands and validation

| Command/check | Outcome |
| --- | --- |
| `git rev-parse HEAD`; `git branch --show-current`; staged/unstaged/untracked status | Captured as above; no staged changes, broad dirty tree. |
| `graphify query "GP-00 core golden path D-01 D-12 R00 R06"` | Completed; 539 nodes, truncated output; returned historical R00–R06 references, confirming graph staleness. |
| `services/core/gradlew.bat --version` with explicit JDK `JAVA_HOME` | Exit 0 per coordinator; Gradle 9.8.2 / Kotlin 2.4.10. No Gradle tests/build run for this documentation-only issue. |
| `$env:JAVA_HOME='C:\Users\joaov\.jdks\azul-21.0.13.0.3'; .\gradlew.bat compileKotlin compileTestKotlin --rerun-tasks --quiet --console=plain` from `services/core` | Exit 0 on the current dirty worktree per coordinator. This proves forced main and test-source compilation only; it does not prove tests, runtime behavior, database/broker availability, or architecture compliance. |
| Docker / PostgreSQL / RabbitMQ availability | Docker and other container CLIs not found; service availability not proven. No claim of live infrastructure. |
| Documentation checks | Relative Markdown target/heading-anchor check passed for `ARCHITECTURE.md`, `core-refactoring-plan.md`, `core-golden-path-issues.md`, and this record; `git diff --check` passed; mojibake/replacement-character scan returned no matches. |

## GP-02 — architecture instrumentation

**Status: instrumentation complete; Core compliance remains pending.** Starting revision was `3fcf980`; current working tree still contains broad pre-existing modifications. Graphify query ran before source inspection; its graph dated 2026-11-17 and source navigation confirmed it predates current changes, so it was treated as stale. Kotlin Gradle plugin/compiler dependency in `services/core/build.gradle` is 2.3.21; the Gradle wrapper runtime reports Kotlin 2.4.10. Added test-only `kotlin-compiler-embeddable:2.3.21`, matching the build plugin/compiler.

| Criterion | Status | Evidence |
| --- | --- | --- |
| D-01–D-10 rule/mechanism/limits matrix, with principle/convention/migration distinctions | verified | [GP-02 evidence](core-golden-path-gp02-evidence.md). |
| Production import selects Core package through ArchUnit and excludes tests; mandatory groups assert non-empty | verified | `CoreArchUnitTests` uses `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`; feature/controller/port/CoreApplication selections assert presence. |
| Shared bounded compiler-aligned PSI analyzer and outside-production fixtures | verified | `CoreArchitectureSourceAnalyzer` is called by both production audit and PSI resource tests. Exact package selectors/reasons are asserted for valid source, policies base/descendant, HTTP/status, filename, and other configured findings. |
| Compatible ArchUnit rules remain mandatory; superseded regex/one-DTO/mandatory `domain.ports` rules removed | verified | Includes nonempty port/policy/controller/dashboard selectors, `ConnectionFactory` prohibition, legacy/target `GetDashboardUseCase` boundary, and compiled positive/negative fixtures. Standard `test` excludes only the production diagnostic class. |
| Compiler-checked generic contract traversal | verified with limits | `CoreContractTypeAnalyzer` binds usage-site type parameters by generic declaration identity and name; compiled fixtures prove nested `Page<Envelope<Item>>`, forwarding a type variable through another project generic, nested `Any?` rejection, unbound generic rejection, and exact transaction callback exception. |
| Diagnostic `architectureAudit` generates complete current report and fails on production findings | verified | `gradlew.bat architectureAudit --console=plain` exited 1 as designed after writing ignored report; current 534 findings / 726 lines / 138,387 bytes. Not a frozen baseline. |
| Isolation and critical shared port behavior contract inventory | verified as inventory | Existing test names and the still-needed no-Spring substitute, shared adapter contract, and PostgreSQL-only guarantees are recorded in evidence. Those runtime guarantees are not verified by GP-02. |
| Structural report and distinct semantic-review issues | verified as separate records | Evidence separates ArchUnit/PSI findings from responsibilities and behavior those tools cannot prove. |
| Production audit passes / Core architecture compliance | pending | Expected D-11 diagnostic failure; remediation belongs to subsequent authorized GP issues. |

The final report records 534 findings across 726 lines. It includes legacy port/use-case locations, obsolete `classroom` references, generic escape and cross-feature contract findings, dependency/cycle details, the disallowed wrapper-shaped transaction runner, and the missing `academic` target package. Semantic interpretation is required against the ownership/export matrix. No PostgreSQL/RabbitMQ service was used or needed for GP-02 fixture/rule checks.

| Command (from `services/core`, explicit JDK 21 `JAVA_HOME`) | Outcome |
| --- | --- |
| `gradlew.bat compileKotlin compileTestKotlin --rerun-tasks --console=plain` | Exit 0; forced compilation of both source sets. |
| `gradlew.bat test --tests '*CoreArchUnitTests' --tests '*CoreArchitectureFixtureTests' --rerun-tasks --console=plain` | Exit 0; nine ArchUnit tests and one PSI fixture test passed. |
| `gradlew.bat architectureAudit --console=plain` | Exit 1 as expected; report written before failing assertion. |

Full `test --rerun-tasks` and `build --rerun-tasks` remain unrun because those broad integration checks require unavailable/unproven PostgreSQL, RabbitMQ, and Testcontainers services. No stage-level service availability claim is made.

Independent review is complete. The ECC reviewer approved the final diff with zero findings after reproducing and rechecking the owner-aware generic forwarding fix. The separate Ponytail reviewer reported no remaining must-fix or could-improve items after rechecking Windows path normalization, both `ConnectionFactory` fixtures, and the earlier contract/dashboard corrections. No database review applied because GP-02 changes only test instrumentation and documentation.

`graphify update .` completed after the source changes and refreshed the ignored local graph to 4,241 nodes / 12,083 edges. Its extraction reported pre-existing syntax-parser warnings in the Gradle script and unrelated Questions/Email Kotlin files, plus unavailable SQL extraction; this does not affect Gradle compilation or architecture checks.

## Next action

Start GP-03 preflight from the committed GP-01/GP-02 dependencies. Reuse the now-proven Docker/Testcontainers PostgreSQL and RabbitMQ path, inventory the existing identity/academic dirty-tree changes before assigning any of them to GP-03, and reproduce affected contract behavior before implementation. Do not interpret the GP-02 expected-failing diagnostic as Core compliance.

## GP-01 — confirmResetDrafts contract regression

**Status: complete.** Starting HEAD: `901bbf053a37791326f9c22af3437a11a74fd0a1`. Worktree was already broadly dirty; preserve all unrelated edits. The focused fix changes `AssessmentPolicy.optionalBoolean` so a missing key keeps the existing false default while a present `null` or other non-boolean raises the existing 422-mapped `IllegalArgumentException`.

| Criterion | Status | Evidence |
| --- | --- | --- |
| Method-level and real HTTP/PostgreSQL explicit-null RED reproduced | verified | The method test failed because null was accepted; the HTTP test returned 200 and revision 2 instead of 422 with unchanged revision. |
| Absence, null, false, true, invalid value types, authorization precedence | verified | New use-case tests cover all requested values; HTTP test verifies absence/null/false/true and student 403 before malformed-value validation. |
| PATCH nullable removal and required field behavior | verified | HTTP/PostgreSQL test confirms `deadline:null` is accepted and missing `expectedRevision` returns 422. |
| Focused assessment integration tests | verified | `AssessmentIntegrationTests`: 24 tests passed. |
| Full forced test suite | run; 3 unrelated failures | 166 tests, 3 failures in Platform rollback, Curriculum admin race, and Forum fresh-process startup; detail in GP-01 evidence. |
| Forced build tasks | verified without test task | `build --rerun-tasks -x test --console=plain` passed. Full build with test was not rerun because it would rerun the known failing suite without a new hypothesis. |
| Independent reviews | verified | Final ECC recheck: APPROVE, zero findings. Final Ponytail recheck: ship, no must-fix or could-improve items. No DB review applies because no persistence/transaction behavior changed. |

Exact commands, outcomes, current Docker/Testcontainers evidence, and the RED/GREEN record are in [GP-01 evidence](core-golden-path-gp01-evidence.md).

Both reviewers initially identified the same test-evidence gap: the nullable deadline-removal assertion started from an already-null value. The executor changed only the fixture to start with a non-null deadline, asserted the stored value before PATCH, reran the four focused method/HTTP/PostgreSQL tests successfully, and the same reviewers approved the targeted recheck. The three unrelated full-suite failures remain recorded and were not changed under GP-01.

`graphify update .` completed after the GP-01 source changes and refreshed the ignored local graph to 4,267 nodes / 12,170 edges. The extractor reported pre-existing unrelated syntax/SQL warnings.

## GP-03 — R-07 identity and academic boundaries

**Review fixes implemented; focused gates and final build pass; forced full test had one non-reproduced outbox timing failure.** Starting revision was `32d022a4d0506729d38bd1be6311b84a048a5707`. Preserve the broad shared dirty worktree; no files were staged or committed. Full scope, acceptance details, and transitional exports are recorded in [GP-03 evidence](core-golden-path-gp03-evidence.md).

The identity and classroom ownership areas were reorganized into `identity.application` and `academic.application`; classroom source/package naming is now academic. Identity operation models moved to `identity.application.models`; academic operation models moved to `academic.application.models`. The atomic email-confirmation transaction coordinator and SQL/lock protocol remain identity-owned and unchanged. `AuthenticatedActor` and `AcademicPage` remain explicit immutable domain-contract compatibility exports for legacy consumers pending GP-04; the actor redacts its internal session validation reference and exports no raw token/cookie/session record. Based on concrete ArchUnit violations, 78 DTO/model declarations moved into their owning application models and 144 directly implicated orchestration declarations moved into application ports/use cases. GP-04 behavior migration for those downstream flows remains pending.

The Platform rollback test previously did not reach the transaction runner because Kotlin `run {}` resolved to stdlib; it now passes `TransactionRunDto` explicitly and the PostgreSQL rollback test passes. The Curriculum last-admin race previously returned `[200,500]`; typed `AcademicConflict` now maps the concurrent loser to 409, and the PostgreSQL race test passes as `[200,409]`.

| Check | Outcome |
| --- | --- |
| `compileKotlin compileTestKotlin --rerun-tasks --console=plain` | Exit 0. |
| Focused `CoreArchUnitTests` + `CoreArchitectureFixtureTests` | Pass. |
| Expanded GP-03 selector (identity/session/Origin/transaction, corrected Platform rollback and Curriculum race, direct identity/academic tests, plus affected announcements/attendance/assessment/curriculum/forum/materials integration suites) | Post-review-fix exit 0; 119 tests in 13 reports, 0 failures/errors; 10m47s. |
| `architectureAudit --rerun-tasks --console=plain` | Final post-review-fix run expected exit 1 after writing report: 313 findings / 503 lines / 107,635 bytes; Core compliance is not claimed. |
| `test --rerun-tasks --console=plain` | Post-fix exit 1 after 12m51: 176 tests, one failure at `OutboxIntegrationTests.publisherRepublishesConfirmedEventWhenOutboxMarkingFails`, line 94 (`published_at is not null` false); zero errors. The method waits for Rabbit message count, then immediately checks the second DB commit, so message visibility before transaction commit is the timing-flake hypothesis. |
| `test --tests '*OutboxIntegrationTests.publisherRepublishesConfirmedEventWhenOutboxMarkingFails' --console=plain` | Isolated method passed, BUILD SUCCESSFUL in 1m28. No production/test edit was made for this non-reproduced failure. |
| `build --console=plain` | Final build passed in 9m54: 176 tests / 29 reports, zero failures/errors; 8 tasks total, only `:test` executed and other compile/package tasks were UP-TO-DATE. |
| `graphify update .` | Post-review-fix refresh completed: 4,503 nodes, 12,437 edges, 241 communities. SQL parser dependency is absent; Gradle and unrelated Email/Questions Kotlin parser warnings remain. Graph artifacts remain local/ignored. |

The architecture report still records structural violations, including an identity–academic feature cycle and legacy/target boundary findings. These are diagnostic evidence, not a clean audit. ECC and Ponytail targeted rechecks approved the three fixes and refreshed evidence. The database reviewer approved persistence/transaction ownership and SQL/locking; no recheck was needed because the three review fixes do not change SQL or transaction coordination. The forced full test showed a single outbox assertion failure that passed in isolation and did not reproduce in the subsequent full build test task.

### Disposition

GP-03 is complete. The one outbox assertion failure is accepted as a non-reproduced timing flake after the isolated method and subsequent full build suite passed. ECC/Ponytail targeted rechecks approve the fixes and evidence; database review is approved. Core architecture compliance and downstream R-07 application migration remain pending later authorized work.

## GP-04.1 — Curriculum

**Status: GP-04.1 complete.** Starting revision `bf8b5a0`. The existing unrelated worktree files are outside GP-04.1. [GP-04.1 evidence](core-golden-path-gp041-evidence.md) records the preflight, scoped architecture RED/GREEN, live receipt compatibility, shared in-memory/PostgreSQL store contract, final gates, and independent reviews.

Curriculum has typed subject, module, order and visit contracts. Pure decisions cover module ordering and observed publication merging. Curriculum translates its own errors, and Dashboard consumes its existing bounded subject-list capability. The receipt codec preserves the historical create fingerprints and receipt format. The underlying PostgreSQL SQL, authorization/lock order and writes retain their existing behavior.

The Ponytail follow-up removed the unused session reference from five store queries, redacted it from seven write and visit DTO descriptions, and protected returned subject and visit lists from source-list and nested mutation. The ArchUnit display name now states the scope of its dependency assertions. The post-fix focused selector passed 44 tests. The final forced Core suite and `build --rerun-tasks --console=plain` each passed 185 tests across 31 suites. All eight actionable build tasks ran in 9m10s. The final production architecture diagnostic reported 197 findings across Core, with zero Curriculum findings. Graphify refreshed the ignored local graph to 4,639 nodes and 12,939 edges. ECC and Ponytail approved after targeted rechecks; the database reviewer approved SQL, lock and receipt equivalence. Host controls did not expose the requested gpt-6-luna-high Ponytail model, so the available gpt-6.2-astra-high reviewer was used and recorded in evidence.


## GP-04.2 - Materials

**Status: GP-04.2 complete.** [GP-04.2 evidence](core-golden-path-gp042-evidence.md) records the acceptance checks, post-fix full gate, and independent reviews.

## GP-04.3 — Assessment authoring

**Status: GP-04.3 complete.** [GP-04.3 evidence](core-golden-path-gp043-evidence.md) records authoring contract validation and the independent ECC and database reviews.

## GP-04.4 — Attempts

**Status: GP-04.4 complete.** [GP-04.4 evidence](core-golden-path-gp044-evidence.md) records the contract validation, full test and build gate, and independent reviews.

## GP-04.5 — Submissions and feedback

**Status: GP-04.5 complete.** [GP-04.5 evidence](core-golden-path-gp045-evidence.md) records typed contracts, real HTTP and PostgreSQL checks, and the independent reviews.
