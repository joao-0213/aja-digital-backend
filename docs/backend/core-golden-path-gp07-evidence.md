# GP-07 evidence — final Core closure

## Scope and outcome

GP-07 closes the Core documentation and evidence record after GP-07.1 through GP-07.3. Its guide links to examples in the verified implementation. [Core golden path guide](core-golden-path-guide.md)

The implementation commits in scope are `9d58231`, `ae44c79`, `48897a3`, `8674ea6`, `bed84bc`, `7c5cc60`, `59bd0d6`, `a58d01e`, and `1700c00`.

The implementation committed as `1700c00` passed the final Core gate from `services/core`.

```sh
B16_OPENAPI_COVERAGE=true ./gradlew test check build architectureAudit --rerun-tasks --no-daemon --console=plain
```

It exited 0 in 8m25s. The test task recorded 242 tests in 49 suites with zero failures, errors, or skips. The mandatory production architecture audit ran through the standard test/check gates, and the separate `architectureAudit` task also reported zero findings. The suite exercised real PostgreSQL, RabbitMQ, and Questions processes. OpenAPI coverage exercised successful responses and expected denials for all 95 public Core operations.

## Decision and refactoring trace

This trace points to existing per-issue records. Criteria and command-level evidence remain in the linked issue files.

| Decision or refactoring | Tracked evidence | Evidence represented |
| --- | --- | --- |
| D-01 feature-oriented domain/application/adapters, D-02 ownership and data boundaries, D-03 capability ports, D-04 typed immutable contracts | [GP-03](core-golden-path-gp03-evidence.md), [GP-04.1–4.9 issue evidence index](core-golden-path-execution.md), [GP-07.1](core-golden-path-gp071-evidence.md) | Package/dependency structure, typed operation contracts, focused behavior and compatibility checks. |
| D-05 transport/application/domain responsibility, D-06 pure policies and transitions | [GP-04.1–4.9 issue evidence index](core-golden-path-execution.md), [GP-07.1](core-golden-path-gp071-evidence.md), [GP-07.3](core-golden-path-gp073-evidence.md) | Direct application tests, behavior tests, transition and authorization review. |
| D-07 atomic write protocols, D-08 idempotency, serialization, and technical shared capabilities | [GP-03](core-golden-path-gp03-evidence.md), [GP-04.2–4.9 issue evidence index](core-golden-path-execution.md), [GP-07.2](core-golden-path-gp072-evidence.md) | Substitute/adapter tests, receipt and byte compatibility, PostgreSQL locking/rollback/concurrency, outbox behavior. |
| D-09 errors and readability | [GP-03](core-golden-path-gp03-evidence.md), [GP-05](core-golden-path-gp05-evidence.md), [GP-07.3](core-golden-path-gp073-evidence.md) | Error category and response mapping checks, source review, public/private error compatibility. |
| D-10 complementary architecture mechanisms, D-11 gradual enforcement with visible findings | [GP-02](core-golden-path-gp02-evidence.md), [GP-07.3](core-golden-path-gp073-evidence.md) | PSI positive/negative fixtures, bytecode dependency checks, mandatory test/check audit, independent semantic review. |
| D-12 opt-in runtime OpenAPI | [GP-06](core-golden-path-gp06-evidence.md) | Default and active profile HTTP checks, normative public contract comparison, UI/browser and packaged process validation. |
| R-07 shared support and cross-cutting boundaries | [GP-03](core-golden-path-gp03-evidence.md), [GP-04.1–4.9 issue evidence index](core-golden-path-execution.md), [GP-05](core-golden-path-gp05-evidence.md), [GP-07.1](core-golden-path-gp071-evidence.md), [GP-07.2](core-golden-path-gp072-evidence.md), [GP-07.3](core-golden-path-gp073-evidence.md) | Feature migration and later typed contracts, shared transaction/idempotency/error/Origin/outbox boundaries. |
| R-08 runtime API docs | [GP-06](core-golden-path-gp06-evidence.md) | Opt-in docs profile and public-only Core contract. |
| R-09 hardened architecture rules and final Core gates | [GP-02](core-golden-path-gp02-evidence.md), [GP-07.3](core-golden-path-gp073-evidence.md) | Rule mechanism and limits, fixtures, zero-finding final audit, full Core gate. |

## Evidence classes and limits

- **Structural compliance:** GP-02 records rule mechanisms, selectors, fixtures, and limits. GP-07.3 records the mandatory production audit in `test`/`check`, the separate zero-finding audit, and feature dependency tests. These checks establish source, package, and bytecode properties. Business meaning is established by behavior evidence and semantic review.
- **Typing:** GP-03, GP-04.1–4.9, GP-07.1, and GP-07.2 record typed inputs, outputs, projections, policies, and encoded receipt contracts. GP-07.1 includes collection-alias tests and historical receipt compatibility.
- **Semantic and behavioral evidence:** per-issue tests and reviews record authorization and validation order, HTTP responses, replay, SQL equivalence, locking, rollback, outbox effects, remote snapshot ordering, and invariants. Direct input-port tests and shared substitute/real PostgreSQL port contracts establish focused behavior. Database-specific constraints require their live database checks.
- **Runtime documentation:** GP-06 verified default and opt-in docs behavior, the public Core OpenAPI projection against the normative contract, Swagger UI cookie flow, route/schema/secret exposure, browser rendering, and packaged Boot JAR startup. The normative contract has 98 operations, of which 95 belong to Core and three to Questions. Email is a worker.
- **Core functional validation:** the exact forced gate above passed on the recorded final code candidate. GP-07 records that historical result. The documentation stage acceptance covers links and whitespace.
- **Integrated acceptance:** pending. The aggregate PowerShell scripts `scripts/acceptance-b16.ps1`, `scripts/acceptance-b03-email-process-restart.ps1`, and `scripts/acceptance-b08-assessment-snapshots.ps1` are pre-existing worktree deletions. The committed B-16 runner expects Windows Docker Desktop, `java.exe`, Gradle batch launch, and Windows PowerShell child paths. This host is Linux. The aggregate run remains pending, and no cross-service acceptance or full-backend validation is claimed from the Core gate. The [tracked issue status](core-golden-path-issues.md#local-issue-order-and-status) records closure state. [GP-06 evidence](core-golden-path-gp06-evidence.md) records separate runtime-doc validation.

The independent focused ECC documentation review on GPT-6.1 Terra at medium reasoning approved the code examples, command and contract claims, and link targets. No actionable findings. The earlier issue records contain the implementation reviews.

## Link and command check

All links resolve to tracked files or the two GP-07 documents added here. The execution record links the GP-04 evidence. The committed service trees match `1700c00`, and the normative OpenAPI, operation ownership and migration files match the baseline at `bc32ca5`.
