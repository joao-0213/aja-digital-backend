# GP-07.3 evidence — Core architecture rules

## Preflight

- Starting revision `a58d01e` on `codex/core-golden-path`. The Git index and Core source tree were clean. The preceding live Core gate passed 239 tests in 48 suites. Java 21, Docker, PostgreSQL and RabbitMQ Testcontainers, and the Questions boot JAR are available.
- `graphify query 'CoreProductionArchitectureAuditTest CoreArchitectureSourceAnalyzer CoreContractTypeAnalyzer CoreArchUnitTests AuthenticatedActor OutboxEventProducerInputPort IdentityEventsTopology LoginStudentDto' --budget 950` located the production audit, rule fixtures and affected entry points.
- `./gradlew architectureAudit --console=plain` at the preceding checkpoint reported two findings. The policy check treated the pure `URI` constructor as I/O, and the contract syntax rule treated the `Any` upper bound of `FieldChange.valueOrNull` as a payload.
- `jdeps -verbose:class -filter:none services/core/build/classes/kotlin/main` and scoped source inspection found existing gaps in published feature contracts. These include private Academic errors reused by four features, internal Identity and Outbox dependencies in consuming features, a transitional authenticated actor alias, and the login command wrapper. The outbox exchange bean is declared in a messaging adapter. The current diagnostic misses these boundaries and is excluded from the standard test task.
- GP-07.3 covers D-01, D-02, D-03, D-06, D-10 and D-11. It preserves public and private error bodies, session behavior, query and write protocols, encrypted token events, and the documented cross-owner SQL.

## Acceptance

| Criterion | Status |
| --- | --- |
| Correct PSI classifications with positive and negative fixtures for the same analyzers | Verified |
| Published feature exports and dependency edges, feature-owned errors, direct login and session input contracts | Verified |
| Source and bytecode enforcement for layer boundaries, wiring, cycles and exact published exports | Verified by fixtures and the production scan |
| Mandatory production audit through `test` and `check`, separate passing `architectureAudit` | Verified |
| Full Core gate with all 95 public operations covered, refreshed Graphify and independent reviews | Verified |

## Implementation checkpoints

- The PSI fixtures prove both classifications. They accept the non-I/O `URI` constructor and the Kotlin `T : Any` bound, and reject an HTTP client import and an untyped payload. The parameterized bytecode traversal remains responsible for resolved generic payloads.
- Login accepts `LoginCommand` directly. Session resolution accepts the nullable cookie directly while keeping the full session result within Identity. `./gradlew compileKotlin --console=plain` and `./gradlew compileTestKotlin test --tests '*CoreArchitectureFixtureTests' --tests '*IdentityApplicationUseCaseTests' --console=plain` passed after correcting stale mechanical call sites. JUnit recorded five tests across the two focused suites with zero failures, errors or skips.
- Identity publishes the concrete, immutable actor and its session-resolution failure. Pure policies receive the necessary role, activity and confirmation facts. Announcements, Assessment, Attendance and Forum own their failures, and their adapters share the existing HTTP response mappings with Academic. The private practice operation maps its own credential failure to the same HTTP body.
- Staff provisioning uses Identity's published password hashing and email validation. Academic and Identity prepare encrypted notifications through an incoming Outbox port. The one typed notification command redacts its token, and the exported encrypted result defensively copies its byte arrays. Each caller retains its existing write transaction.
- `./gradlew compileKotlin compileTestKotlin --console=plain` and the existing `./gradlew architectureAudit --console=plain` passed at this intermediate checkpoint. The stronger rules and standard build integration are the remaining criteria.
- Independent `jdeps -verbose:class -filter:none services/core/build/classes/kotlin/main` inspection found 17 non-shared feature-to-feature edges in the compiled service. The targets are the named input ports and application contracts recorded in the export matrix. An ordered baseline comparison found all 206 SQL fragments in the six touched JDBC adapters unchanged.
- The production ArchUnit rules confirm the module selectors, named consumer-to-provider exports, cycles, application/domain isolation, adapter direction, policy effects and Spring wiring. They also check bean methods on classes without `@Configuration`. Kotlin PSI still owns source placement and syntax checks. The default `test` exclusion for the production audit was removed, and Gradle `check` exercises that test.
- `./gradlew test --tests '*CoreArchUnitTests' --tests '*CoreArchitectureFixtureTests' --console=plain --no-daemon` passed 19 tests in two suites with zero failures, errors or skips. Earlier focused runs exposed missing shared bytecode names and a selector that missed packages ending in `.configuration`. The final focused run uses exact shared infrastructure names and recognizes package segments, including the negative Spring lite `@Bean` fixture.
- `./gradlew architectureAudit --console=plain --no-daemon` exited 0 with an empty `build/reports/architectureAudit/core-violations.txt` after correcting the standalone test's configuration package selector as well.

## Full Core gate

From `services/core`, `B16_OPENAPI_COVERAGE=true ./gradlew test check build architectureAudit --rerun-tasks --no-daemon --console=plain` exited 0 in 8m25s. The standard test task ran 242 tests in 49 suites with zero failures, errors or skips. Its reports include `CoreProductionArchitectureAuditTest`. The separate audit task also passed and wrote zero findings. The real PostgreSQL, RabbitMQ and Questions tests ran, and the Core OpenAPI assertions covered successful requests and expected denials for all 95 public Core operations.

The [feature rules](../../services/core/src/test/kotlin/br/com/ajadigital/core/CoreFeatureDependencyRules.kt) enumerate the 17 directed edges and their exact types. The [ownership and export matrix](core-golden-path-issues.md#ownership-exports-and-dependency-matrices) documents the operations and data dependencies. The test proves class and package boundaries. The code and database reviews cover error meanings, side-effect order and persistence equivalence.

`graphify update .` from the repository root passed with 5,627 nodes and 15,941 edges. SQL remains outside its extractor because the local SQL parser is unavailable. The independent SQL comparison and live tests supply the scoped SQL evidence. `git diff --check` passed.

## Independent review

- ECC on GPT-6.1 Terra at medium reasoning approved the mandatory checks and fixtures, exact exported dependencies, response mapping, session contracts, and password and notification preparation. No actionable findings.
- The registered PostgreSQL reviewer on GPT-6.1 Sol at medium reasoning approved the SQL, lock order, revalidation, rollback, encoded receipts, and outbox payload and exchange behavior. Its comparison normalized the local exception names and email-validator reference in the six unchanged JDBC protocols. No actionable findings.
