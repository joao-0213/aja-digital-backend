# GP-02 architecture instrumentation evidence

**Status: instrumentation complete; Core architecture is not compliant.** Starting revision: `3fcf980` (`docs(core): plan golden path execution`). The checkout had broad pre-existing modifications, which remain outside this issue's acceptance. D-11's special diagnostic treatment applies: fixture/rule tests pass; `architectureAudit` is expected to fail on the current production tree and emits the complete current report at `services/core/build/reports/architectureAudit/core-violations.txt` (local build output, not committed).

## Rule/mechanism/limits matrix

| Decisions | Rule and mechanism | Fixture or evidence | Limits / status |
| --- | --- | --- | --- |
| D-01 | ArchUnit bytecode dependency rules enforce domain inward isolation and adapter inbound dependency; bounded PSI reports legacy `domain/ports` and `domain/usecases` file placement. | `CoreArchUnitTests`; valid source plus legacy-placement fixture. | PSI reports package/import syntax and cannot prove responsibility separation. Placement findings are diagnostic during migration. |
| D-02 | ArchUnit slice cycle check; PSI flags the obsolete `classroom` name; contract graph follows port method usage-site types and project property fields with a visited set. | Production inventory report. | Slices identify package cycles, not data ownership or SQL joins. Contract graph does not infer business ownership or distinguish every approved published value; manually reconcile against the export/ownership matrix and review. `academic` conversion remains pending. |
| D-03 | Existing port suffixes are selected from bytecode; source audit tracks old/new port package placement. | Non-empty production port selection. | No mandatory one-DTO rule; no requirement that each operation use a wrapper. Capability cohesion and names need semantic review. |
| D-04 | `CoreContractTypeAnalyzer` checks declared port method signatures and traverses project contract fields/properties with a visited set. ArchUnit `JavaType` parameterized arguments are carried into project property traversal, keyed by generic declaration and variable name. | Compiled fixtures cover nested `Page<Envelope<Item>>`, `Map<String, List<Any?>>`, unbound `T`, generic data-class properties, and valid/invalid transaction-runner signatures. | The walker follows only project types imported into its package scope; library internals are leaves. It binds variables where ArchUnit exposes `JavaParameterizedType` arguments; unbound variables are reported. It does not infer Kotlin nullability, aliases, constructor/property semantic equivalence, deep aliasing or business-state validity. Generated data methods (`equals(Object)`, `copy`, `componentN`) are excluded. |
| D-05 | Bytecode layer rules and direct input-port test inventory; HTTP mapping/error precedence remains behavioral. | Existing identity/session/feature integration tests listed below. | These integration tests run through Spring and are not evidence of no-Spring authorization or invalid-state behavior. That isolation coverage remains pending. |
| D-06 | ArchUnit plus PSI import checks reject policy dependencies on ports, adapters, configuration, framework and I/O imports. | Policy port negative fixture. | Imports/dependencies cannot prove purity, effect order, cohesion, or that calls through innocently named helpers do not perform I/O. Semantic review and pure policy tests remain required. |
| D-07 | Generic traversal permits exactly the technical type exception `<T> run(block: () -> T): T` on `TransactionRunnerOutputPort`; the compiled fixture accepts that shape and rejects its `TransactionWrapper<T>` form. | Positive/negative transaction-runner signature fixtures plus existing `SpringTransactionRunnerTests`. | This is only a type-signature exception; it proves no atomicity, lock order, rollback, concurrency, or transaction protocol. Those require real PostgreSQL. No persistence change is authorized by GP-02. |
| D-08 | ArchUnit diagnostic prohibits shared code from depending on feature packages. The contract type walk may surface overly generic serializer/idempotency signatures as D-04 findings. | Shared-to-feature positive/negative compiled fixture; current audit checks production shared package. | No specialized rule proves operation-specific serializer/hash capabilities, fingerprint stability, historical bytes, receipt compatibility, TTL, retries, or crypto. Those remain behavioral/semantic checks, not D-08 pass claims. |
| D-09 | ArchUnit checks controller transaction/persistence boundaries, forbids `ConnectionFactory` in controllers, and protects `GetDashboardUseCase` from concrete use cases in old and target packages. The shared PSI analyzer reports domain/application HTTP status imports, `!!`, suppression annotations, and multiple/mismatched primary declarations; permits private helpers, named small result types, and directly named sealed subtypes. | Compiled positive/negative boundary fixtures and PSI resources call the same analyzer used by production audit. | Filename structure checks class/object declarations; it does not resolve all top-level functions/type aliases, prove helper cohesion, infer HTTP error precedence/safe causes, or type-check nullability. Semantic and contract tests remain required. |
| D-10 | ArchUnit for bytecode boundaries/contracts/cycles; Kotlin compiler-aligned PSI for bounded syntax/package rules; behavioral contract tests for runtime semantics; independent semantic review. | Positive/negative resources live under `src/test/resources/architecture`, outside production package import. | No custom parser/type checker; production import uses package/classpath plus `DO_NOT_INCLUDE_TESTS`, not a fixed classes directory. Rule selectors assert non-empty required groups. |
| D-11 | Fixture/rule tests are in `test`; a separate `architectureAudit` `Test` task runs only the production audit and writes before failing. Standard `test` excludes only that diagnostic class. | Focused test command passes; diagnostic task fails and writes report. | No baseline, snapshot waiver, broad exclusion, or global empty-selection allowance. The diagnostic's expected failure is not Core compliance. |

## PSI fixture coverage

`CoreArchitectureFixtureTests` parses test resources with `CoreArchitectureSourceAnalyzer`, the exact analyzer used by the production audit. It uses `kotlin-compiler-embeddable:2.3.21`, matching the Kotlin Gradle plugin/compiler dependency configured for Core. Compiled contract fixtures are separate from PSI syntax fixtures; the wrapper runtime reports Kotlin 2.4.10, distinct from the declared plugin version.

| Fixture | Expected result |
| --- | --- |
| `valid/FeatureFile.kt` | accepted |
| `invalid/DomainFrameworkImport.kt` | domain framework import |
| `invalid/GenericContractEscape.kt` | `Any?` in contract |
| `invalid/PolicyPortImport.kt` | policy imports port |
| `invalid/LegacyUseCasePlacement.kt` | use case remains under domain |
| `invalid/ApplicationAdapterImport.kt` | application imports adapter |
| `invalid/ClassroomFeature.kt` | obsolete classroom feature name |
| `invalid/WrongName.kt`, `invalid/MultipleDeclarations.kt` | mismatched primary filename and unrelated second primary declaration |
| `invalid/HttpStatusInDomain.kt`, `invalid/UnsafeAssertion.kt`, `invalid/Suppression.kt` | D-09 HTTP leakage and unsafe syntax |
| `valid/BaseEvent.kt` | sealed family allowed in its primary file |

The policy selector matches exactly `.domain.policies` and descendants (the fixtures cover both).

Compiled ArchUnit fixtures check controllers holding both Rabbit client and Spring AMQP `ConnectionFactory` types, plus old/target dashboard use cases holding concrete `*UseCase` dependencies. Their compliant controller/model/input-port variants pass the same production rules. Compiled contract fixtures also verify concrete generic forwarding through one project type into another; the shared PSI analyzer accepts a valid file when given a Windows-backslash path.

The superseded regex-based `ArchitectureTests.kt` and old mandatory port DTO/`domain.ports` rules were removed. Existing compatible ArchUnit guarantees remain mandatory in `CoreArchUnitTests`.

## Port isolation and critical shared-contract test inventory

| Capability / operation | Existing evidence | Gap retained for later flow issues |
| --- | --- | --- |
| Direct feature input-port invocation without HTTP/Spring | `IdentityIntegrationTests`, `AssessmentIntegrationTests`, `MaterialsIntegrationTests`, `ForumIntegrationTests`, and `CurriculumIntegrationTests` exercise behavior through service composition. | Add focused application/use-case tests with deterministic port substitutes and no Spring/database for authorization, preconditions, and transition behavior. |
| Transaction runner and rollback | `SpringTransactionRunnerTests`. | Existing test checks transaction adaptation; PostgreSQL remains required for lock/isolation/concurrency guarantees. |
| Idempotency replay and serialization fingerprints | `AesGcmOutboxPayloadProtectorTests`, `PublishPendingEventsTests`, and feature integration tests exercise related paths. | Shared port-contract suite must compare old/new bytes, field ordering, absent/null, numeric forms, replay receipts, and failure rollback against real adapter where relevant. |
| Snapshot provider | `HttpQuestionSnapshotProviderTests`; assessment integration coverage. | Run direct consumer tests with substitute and preserve network-before-transaction/revalidation order; live service tests are separate. |
| Shared clock, pagination, partial-update contracts | Current test suite contains usage-specific assertions. | Add only where a migrated flow consumes them; don't create a generic contract framework. |

ArchUnit fixture tests compile test-only package types and import only the explicit fixture classes to prove the domain, inbound/outbound adapter direction, shared-to-feature, and cycle rules each reject a bad dependency while accepting a same-side inbound model/facade and pure domain value. The production scan uses `ImportOption.Predefined.DO_NOT_INCLUDE_TESTS`.

These rows inventory current tests and gaps; they do not claim the cross-cutting behavior is verified by GP-02.

## Diagnostic production inventory

`architectureAudit` was run after the final GP-02 analyzer change on 2026-11-17. It exited **1 as expected** with 534 findings and wrote a 726-line / 138,387-byte report. Expanded ArchUnit details span multiple report lines, so line count is larger than the finding count. The report is intentionally generated in ignored build output rather than checked in as a snapshot. Current findings include:

| Finding group | Count in current report | Interpretation |
| --- | ---: | --- |
| Legacy placement and feature naming | Included | Legacy port/use-case locations and `classroom` references remain pending staged migration; the report also records the missing `academic` target package. |
| Contract graph | Included | Generic escape and cross-feature contract findings require inspection against the export matrix. The permitted direct transaction callback shape is covered by fixtures; the current legacy wrapper signature remains disallowed. |
| Dependency direction and cycles | Included | ArchUnit reports expanded dependency/cycle context. Shared-to-feature and adapter-direction rules are also evaluated. |
The report is a diagnostic inventory only. Do not use it as a baseline or suppress findings. Re-run the task after fixes and inspect the actual report.

### Separate semantic-review issues

ArchUnit/PSI cannot decide whether a helper owns business workflow, whether a DTO/value is a legitimate published immutable contract, whether a policy is cohesive and effect-free through its call graph, whether transaction ordering preserves the contract, or whether a SQL cross-owner read is authorized. Review those against `ARCHITECTURE.md`, the ownership/export and data-dependency matrices, and operation-specific tests. It also cannot establish the HTTP error category/precedence, suppressed-warning necessity, or actual immutability. No semantic conclusion is claimed here.

## Validation

All commands ran in `services/core` with `JAVA_HOME=C:\Users\joaov\.jdks\azul-21.0.13.0.3`:

| Command | Outcome |
| --- | --- |
| `gradlew.bat compileKotlin compileTestKotlin --rerun-tasks --console=plain` | Exit 0; both source sets were forced to compile. |
| `gradlew.bat test --tests '*CoreArchUnitTests' --tests '*CoreArchitectureFixtureTests' --rerun-tasks --console=plain` | Exit 0; nine ArchUnit tests and one PSI fixture test passed. |
| `gradlew.bat architectureAudit --console=plain` | Exit 1 as designed; 534 findings, 726 lines / 138,387 bytes; report written before assertion failure. |

TDD/run record: initial `gradlew.bat compileTestKotlin --console=plain` exited 1 because the PSI harness used Kotlin's K1-deprecated project/factory API without its opt-in; `gradlew.bat compileKotlin compileTestKotlin --console=plain` then exited 0 after the opt-in. The initial `gradlew.bat test --tests '*CoreArchUnitTests' --tests '*CoreArchitectureFixtureTests' --console=plain` fixture run exited 1 because invalid fixture declarations did not match their filenames, so the new structure checker correctly reported an extra reason. I renamed the fixtures to isolate the intended rule reason and reran the same command to exit 0. Those were harness REDs, not production behavior regressions. The final negative fixture assertions prove each configured failure reason directly; no production behavior was changed in GP-02.

Full `test --rerun-tasks`/`build --rerun-tasks` were not run: existing integration tests require unavailable/unproven PostgreSQL/RabbitMQ/Testcontainers services. The test task depends on `bootJar`; the focused run passed. No service-backed architecture or semantic claim is made.
