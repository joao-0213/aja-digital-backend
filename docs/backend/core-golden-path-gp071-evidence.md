# GP-07.1 evidence — Academic typed contracts

## Preflight

- Starting revision `7c5cc60cd9927a2b8dbb94ae872796ae50956fec` on `codex/core-golden-path`. The Git index and Core service tree were clean. Existing documentation and repository changes are outside this issue.
- The final-closure baseline `./gradlew architectureAudit --console=plain` exited 1 with 47 findings. Thirty-nine describe generic Academic input or store contracts. The other eight belong to the shared technical and rule follow-ups.
- `graphify query 'AcademicController JdbcAcademicStore AcademicStoreOutputPort ListClassesUseCase CreateEnrollmentUseCase CoreArchUnitTests pending types GP-07' --budget 900` located the Academic controller, application and JDBC boundaries.
- Scope is the B-04 Core public operations for users, classes, students and enrollments, including the caller’s own enrollment list. The Academic-owned Dashboard read, Identity token confirmation protocol and transactional outbox remain part of the behavior to verify.
- Java 21, Gradle, PostgreSQL and RabbitMQ Testcontainers are available. The Questions boot JAR is available for the final full Core checks.
- Baseline `./gradlew test --tests '*PlatformIntegrationTests' --tests '*AcademicApplicationUseCaseTests' --tests '*DashboardIntegrationTests' --tests '*IdentityIntegrationTests' --console=plain` passed 42 tests in 52 seconds with zero failures, errors or skips across all four suites.

## Acceptance

| Criterion | Status |
| --- | --- |
| Typed Academic input and output ports, immutable values and explicit page/view shapes | Verified |
| Existing HTTP mapping, field omission, error precedence, and replay and fingerprint compatibility | Verified by focused tests and source comparison |
| Existing SQL and bindings, authorization, locks, revision and atomic writes | Verified |
| Live focused and full tests, Graphify, zero Academic audit findings and independent reviews | Verified |

## Boundary changes

- Academic input ports and JDBC projections return typed user, class, enrollment, student and preview values. Paginated operations use the existing `Page<T>`. Class roster IDs, enrollment assessment IDs and nested student enrollments are copied and exposed as unmodifiable lists.
- Existing operation request class names were retained for this projection migration. The issue added no request wrappers.
- The HTTP adapter preserves field order and omits `studentIds` when a student reads a class. Staff still receive the roster field, including an empty list. Student detail continues to emit the three existing empty attendance, attempt and submission arrays. The Academic result carries only the student and enrollment projections available from its query.
- The six idempotent Academic writes retain their existing request inputs, keys and fingerprint production. Their typed receipts use the existing encoded storage port. The feature's Jackson adapter maps persisted document text to the corresponding view.
- An ordered comparison against `7c5cc60` found 70 identical SQL fragments in `JdbcAcademicStore`. Scope and mutation checks, lock acquisition and transaction boundaries still execute in their existing order. The audit found zero findings in Academic after the types were introduced. Eight findings remained elsewhere.

## Validation

Commands ran from `services/core` unless specified.

| Command | Result |
| --- | --- |
| `./gradlew compileKotlin compileTestKotlin --console=plain` | Passed. |
| `./gradlew test --tests '*PlatformIntegrationTests' --tests '*AcademicApplicationUseCaseTests' --tests '*DashboardIntegrationTests' --tests '*IdentityIntegrationTests' --console=plain` | Passed after updating the typed test doubles. The new aliasing test replaces the two legacy map-page assertions and checks the typed collections. |
| `./gradlew test --tests '*CurriculumIntegrationTests.academicMutationsWriteMinimalAuditAndIdempotentReplayDoesNotDuplicateIt' --console=plain` | Passed on retry after Testcontainers Ryuk could not connect to its dynamic local port before application startup. The live check seeds the old ordered class receipt shape, verifies response and persisted bytes, replays through the typed decoder and checks that the audit event count stays unchanged. |
| `./gradlew architectureAudit --console=plain` | Exit 1 with eight findings outside Academic. The remaining technical and source-rule findings stay visible for GP-07.2 and GP-07.3. |
| `./gradlew test build --rerun-tasks --console=plain` | Passed in 7m26s; all eight tasks executed. XML records 237 tests in 47 suites, with zero failures, errors or skips. Real PostgreSQL, RabbitMQ and Questions were exercised by the Core suite. |
| `graphify update .` from the repository root and `git diff --check` on the service diff | Passed. |

## Independent review

- ECC on GPT-6.1 Terra at medium reasoning approved the typed contracts, field omission, authorization and historical receipt mapping. No actionable findings.
- The registered PostgreSQL reviewer on GPT-6.1 Sol at medium reasoning approved the unchanged SQL, bindings, locking, transaction boundaries and historical receipt compatibility. It independently compared 65 complete executable SQL statements in order as a separate check from the 70-fragment comparison. No actionable findings.
