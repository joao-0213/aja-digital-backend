# GP-04.1 evidence — Curriculum

**Status:** GP-04.1 complete. Implementation, validation and independent reviews are complete. Starting revision: `bf8b5a0e205b39badf5a0fa0dc052aa13d1bb153` (`refactor(core): establish identity and academic boundaries`). No staged changes. The unrelated `.gitignore` edit, local multiagent skill and orchestration prompt are outside this issue. The available host executor is GPT-6.2 Sol at medium reasoning because the configured Luna executor is unavailable.

## Preflight

- Reviewed D-01–D-10, R-07/R-09, architecture, the OpenAPI 1.0.8 Subjects, Modules, Visit and Order schemas/operations, operation ownership and the ownership/export/code/data/transaction matrices. Scope is the eleven Curriculum HTTP operations and the existing Dashboard subject-list dependency.
- Starting Graphify graph contained 4,519 nodes. `graphify query "curriculum subject module visit reorder policy application store authorization cross-feature dependency" --budget 2500` identified the policy, input/write ports, use cases and their actor and Academic dependencies. A second Core integration query identified Testcontainers and the test base.
- Azul JDK 21.0.13 (`C:\Users\joaov\.jdks\azul-21.0.13.0.3`), Gradle Wrapper 9.8.2 and PostgreSQL/RabbitMQ were available. Testcontainers worked with Docker CLI absent from PATH. Baseline `gradlew.bat test --tests '*CurriculumIntegrationTests' --console=plain` passed 18 tests, zero errors/failures (86.169 seconds in the suite, 2m48s Gradle).

## Architecture correction recorded before implementation

| Evidence | Decision and smallest correction | Acceptance evidence |
| --- | --- | --- |
| Input/output ports return raw maps and Academic's transitional page. The JDBC adapter builds dynamic maps, including values the Curriculum policy reads back for conflicts. Dashboard consumes the existing subject-list capability. | D-01–D-06/D-09: publish only the existing subject-list contract, type Curriculum writes/reads/transition facts, use typed internal pagination, and keep HTTP projections at the adapter. A shared business model or an extra Dashboard query would exceed the required edge. | Scoped contract architecture test, direct input-port tests, and live HTTP and Dashboard tests. |
| Both creates use `JsonSerializerOutputPort.serialize(Any?)` and `IdempotencyStoreOutputPort` exposes `Map<String, Any?>` on read and write, so passing a typed result through the old API still requires the application to interpret receipt field names or to expose a raw receipt. | D-08: an explicit Curriculum codec owns the existing request/receipt representation. A shared encoded-receipt interface reuses the existing JDBC statements, locking and expiry; legacy callers retain their old interface while out of scope. Avoid a generalized codec registry and avoid duplicating receipt SQL. | Golden legacy receipt and request bytes/hashes, PostgreSQL replay/conflict/expiry behavior and DB review. |

Legacy hash input is compact Jackson UTF-8 JSON with the fields in insertion order. Subject uses `classId, name, teacherIds, color, icon, description`. Module uses `subjectId, title, description`. Text is hashed before trimming and teacher-array order matters; PATCH is not receipt-backed. Each create uses SHA-256 lowercase hex, its existing operation and target name, and the actor and key. Subject's receipt has the write response fields `id, revision, createdAt, updatedAt, updatedBy, classId, name, teacherIds, color, icon, description`. Module's receipt has `id, revision, createdAt, updatedAt, updatedBy, subjectId, title, description, order`. The shared JDBC table stores the response as text with a 24-hour expiry, locks the actor/operation/target/key tuple through its existing PostgreSQL advisory lock, and permits replacement only after expiry. Permanent Assessment receipts are outside the touched storage path.

## Implementation and evidence

- Subject and module commands carry typed patches, revisions and results. Subject listing publishes the existing bounded capability used by Dashboard. Curriculum owns its business errors and translates them at its HTTP boundary. The controller passes the cookie value directly to the published identity port, which retains its request overload for existing callers.
- The two create use cases own authorization, legacy-fingerprint lookup, the write and receipt save inside a transaction. Their typed codec handles the established receipt format, including rows without a separate version marker. The shared JDBC implementation uses the same PostgreSQL statements and clock/encoding order for legacy callers. Other features still use the transitional map interface. GP-04.1 uses the callback transaction method; the DTO overload remains available to existing callers.
- Module reordering and visit merging use pure decisions on locked facts. The adapter retains the existing advisory session-gate, class, teacher, subject and module lock sequence, scoped reads, SQL effects, audit event names and revision updates. Its three revision writes expose applied, conflict and missing results using the existing fallback lookup inside the same transaction. The rendered SQL is equivalent with typed fields and the same number/order of class placeholders.
- Compared the 49 Curriculum JDBC SQL literals and three shared receipt store literals against `bf8b5a0`. The statements match after replacing the old inline class-placeholder expression with its equivalent named variable.
- The four isolated tests cover authorization precedence, pagination rejection, blank/invalid state, typed write errors and defensive snapshots for order and visit lists. The shared reorder/visit contract executes against both the in-memory implementation and the real PostgreSQL port, covering absence, authorization, stale or incomplete order, bounded results, and merging only authorized publications. Concurrency, database locks and SQL state remain verified against PostgreSQL.
- `liveCurriculumPreservesLegacyReceiptBytesAndReplaysExistingReceipts` verifies the literal legacy hashes and receipts for both creates against the database. It seed-replays both legacy receipts, preserves teacher-array order and whitespace in the request fingerprints, and tests expiration, key conflict, PATCH omission/null/empty-array behavior, and typed subject list output through live HTTP.

### Commands

All Gradle commands were run from `services/core` with the JDK above.

| Command | Outcome |
| --- | --- |
| `gradlew.bat test --tests '*CoreArchUnitTests.curriculum ports*' --console=plain` | RED. Exit 1, 1 test failed in 51s because Curriculum ports exposed `java.lang.Object` and Academic's internal page. The diagnostic also misclassified the published transitional identity actor. |
| `gradlew.bat compileKotlin compileTestKotlin --console=plain` | Exit 0, 1m17s. The first incremental compile exposed a Kotlin query-interpolation typo and ambiguous Jackson tree iteration. This run followed their correction. |
| `gradlew.bat test --tests '*CurriculumApplicationTests' --tests '*CurriculumIntegrationTests' --tests '*DashboardIntegrationTests' --tests '*CoreArchUnitTests' --tests '*CoreArchitectureFixtureTests' --console=plain` | First focused run: 41 of 42 passed in 3m14s, including the scoped RED→GREEN, direct tests, Dashboard and the shared PostgreSQL contract. The receipt fixture sampled a moving clock after HTTP and expected different precision from PostgreSQL. It now freezes the clock to whole seconds before making requests. |
| Same focused selector | Second run: 41 of 42 passed in 4m15s. The frozen clock verified the two exact hashes and receipts, both seeded replay results and PATCH compatibility. The final expiry fixture tried to set expiry equal to creation, which V6 prohibits. The fixture now represents an actual 24-hour receipt expiring at the current instant. |
| `gradlew.bat test --tests '*CurriculumIntegrationTests.liveCurriculumPreservesLegacyReceiptBytesAndReplaysExistingReceipts' --tests '*CoreArchUnitTests.curriculum ports*' --console=plain` | Exit 0 in 3m17s. Both tests passed with the corrected expiry fixture and the published identity port overload. |
| `gradlew.bat architectureAudit --console=plain` | First diagnostic exited 1 in 31s and produced 198 findings. Its single Curriculum finding was the top-level `SubjectTeacher` declaration in `SubjectSummary.kt`. The two declarations now have their own files with the same package and signatures. |
| Same broader focused selector | Exit 0 in 2m32s. All 42 passed. Counts were architecture 11, isolated application 4, Curriculum HTTP/PostgreSQL 20, Dashboard HTTP/PostgreSQL 7. The forced full suite and next production diagnostic validate the final physical split of the type files. |
| `gradlew.bat test --rerun-tasks --console=plain` | First full run: exit 1, 182 of 183 passed in 10m08s. The existing `IdentityApplicationUseCaseTests.feature actor resolution exposes only published actor facts` expected exactly one method named `resolve`. The compatible overload exposed that reflection assumption. The test now calls both overloads and checks that every `resolve` returns only the published actor type. Runtime integration suites passed. |
| `gradlew.bat test --tests '*IdentityApplicationUseCaseTests.feature actor resolution*' --tests '*CurriculumApplicationTests' --tests '*CurriculumIntegrationTests' --tests '*DashboardIntegrationTests' --tests '*CoreArchUnitTests' --tests '*CoreArchitectureFixtureTests' --console=plain` | Exit 0 in 2m13s. All 43 passed after the identity assertion correction, including the final layout and the affected live HTTP/PostgreSQL cases. |
| `gradlew.bat test --rerun-tasks --console=plain` | Pre-review-fix forced run exited 0 in 9m37s. All 183 tests across 30 suites passed, with zero failures, errors or skips. Superseded by the post-review runs below. |
| `gradlew.bat build --console=plain` | Interim incremental build exited 0 in 40s and reused the then-current forced compilation, package and test results. Superseded by the post-review forced build below. |
| `gradlew.bat build --rerun-tasks --console=plain` | Pre-review-fix forced build exited 0 in 10m. All eight actionable tasks and lifecycle tasks `assemble`, `check` and `build` completed; its 183-test result is historical. Superseded by the post-review forced build below. |
| `gradlew.bat architectureAudit --console=plain` | Pre-review-fix diagnostic exited 1 in 31s. The 387-line report contained 197 findings across Core and zero Curriculum findings. Superseded by the post-review diagnostic below. |
| `graphify update .` | Exit 0 after the test correction; ignored local graph refreshed to 4,626 nodes and 12,895 edges. Graphify reported absent optional SQL parsing and the previous unrelated source parser warnings. |

## Ponytail findings and corrections

- D-02. Kotlin's generated `toString()` printed the opaque session-validation reference on seven write and visit DTOs. `StoreAuthorizeSubjectCommand`, `StoreCreateModuleCommand`, `StoreCreateSubjectCommand`, `StoreReorderModulesCommand`, `StoreUpdateModuleCommand`, `StoreUpdateSubjectCommand` and `StoreVisitSubjectCommand` now redact it. The source inspection identified five ordinary reads with an unused reference: `StoreListSubjectsQuery`, `StoreSubjectQuery`, `StoreSubjectModulesQuery`, `StoreModulesQuery` and `StoreModuleQuery`. The read DTOs and their callers now omit it. Visit read/write retain session revalidation through the store.
- D-04. `Subject`, `SubjectSummary` and `SubjectVisit` now copy their input lists and expose unmodifiable views of the copies, matching the existing page contract. The regression test mutates the four source lists and attempts mutation through every exposed list, including the teacher IDs of the nested subject, to verify all five views.
- The focused architecture guard's display name now identifies Academic and selected Identity packages. Source review separately checked all cross-feature imports. The guard's assertions are unchanged.

| Command | Outcome |
| --- | --- |
| `gradlew.bat test --tests '*CurriculumContractSafetyTests' --console=plain` | RED in 24s, exit 1. `write and visit command descriptions redact the session reference` detected the fictitious reference in `StoreAuthorizeSubjectCommand`. `subject projections snapshot and protect nested teacher and publication lists` observed the caller's cleared nested teacher IDs. |
| `gradlew.bat compileKotlin compileTestKotlin --console=plain` | Exit 0 in 57s after applying both fixes. |
| Same `CurriculumContractSafetyTests` selector | GREEN in 11s, exit 0, both tests passed. The redaction case checks all seven command classes, and every exposed collection rejects direct mutation with `UnsupportedOperationException`. |
| `gradlew.bat test --tests '*CurriculumContractSafetyTests' --tests '*CurriculumApplicationTests' --tests '*CurriculumIntegrationTests' --tests '*DashboardIntegrationTests' --tests '*CoreArchUnitTests' --tests '*CoreArchitectureFixtureTests' --console=plain` | Exit 0 in 1m45s, all 44 passed. The live HTTP/PostgreSQL tests confirmed both byte-exact legacy receipts, replay and expiry, session revalidation and pagination with the revised contracts. |
| `gradlew.bat test --rerun-tasks --console=plain` | Exit 0 in 9m12s, 8/8 actionable tasks executed and all 185 tests across 31 suites passed with zero failures, errors or skips. |
| `gradlew.bat test --tests '*CoreArchUnitTests.curriculum ports have typed contracts and avoid Academic and selected Identity packages*' --console=plain` | Exit 0 in 34s. The same rule passed under its accurately scoped display name. |
| `graphify update .` | Exit 0 after the display-name edit. The ignored graph has 4,639 nodes and 12,939 edges. The extractor reported absent optional SQL parsing and the previous unrelated source parser warnings. |
| `gradlew.bat build --rerun-tasks --console=plain` | Final post-review build exited 0 in 9m10s. All eight actionable tasks ran, `assemble`, `check` and `build` completed, and fresh XML confirms all 185 tests across 31 suites passed with zero failures, errors or skips. |
| `gradlew.bat architectureAudit --console=plain` | Final diagnostic exited 1 in 22s and emitted 197 findings across Core in a 387-line report, with zero Curriculum findings. |

## Acceptance checklist

| Criterion | Status |
| --- | --- |
| Typed ports, pure rules, declared Dashboard edge and no implementation imports from other features | verified by the scoped architecture guard for Academic and selected Identity packages, plus source review of all cross-feature imports |
| Direct input tests and shared atomic store behavior contract | verified by direct tests and real PostgreSQL port test |
| Real HTTP, PostgreSQL and receipt/revision/pagination compatibility | verified by Curriculum and Dashboard integration evidence |
| Focused and full Gradle gates and Graphify refresh | verified |
| Independent ECC, Ponytail and database review | approved. ECC used gpt-6.1-terra medium. Ponytail used available gpt-6.2-astra high because host controls did not expose requested gpt-6-luna high. Database reviewer is fixed to gpt-6.1-sol medium. All three reviews were read-only; their independent test execution was not claimed. |

## Review disposition

- ECC approved the contracts and final forced-build evidence; targeted rechecks approved the credential-redaction and immutable-result fixes.
- Ponytail approved the typed Curriculum boundaries and receipt compatibility, then approved both confirmed fixes and the accurately scoped ArchUnit test name. The requested Luna-high model was unavailable in host controls; the available Astra-high reviewer was used and recorded above.
- The registered database reviewer approved the SQL, lock, transaction and receipt equivalence; it did not independently run PostgreSQL tests.
- The initial full run exposed a stale reflection test assumption and review exposed session-reference exposure through generated `toString()` plus mutable-list aliases. All were fixed, covered by regression tests, and rechecked by the same reviewers. No unresolved blocker remains.
