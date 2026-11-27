# GP-04.8 evidence — Attendance application

## Preflight

- Starting revision `ae44c798a6f9c408e02584ced938d95312fd650c` on `codex/core-golden-path`. The Git index and Core service tree were clean. Existing documentation and repository changes are outside this issue.
- `graphify query 'AttendanceController AttendanceIntegrationTests JdbcAttendanceStore CreateAttendanceUseCase attendance eligibility GP-04.8' --budget 850` located the HTTP entry point, PostgreSQL store, integration tests and current outgoing port.
- Scope covers `GET` and `POST /attendance`, `GET /attendance/eligible`, and `GET` and `PATCH /attendance/{id}`. Core owns authorization, historical eligibility, session uniqueness and revisions. Student reads expose only their own marking, and staff reads use the authorized context.
- Java 21 and the Gradle wrapper are available. The existing Testcontainers harness runs real PostgreSQL and RabbitMQ, and the Questions boot JAR remains available for the full Core suite.
- Baseline `./gradlew test --tests '*AttendanceIntegrationTests' --console=plain` in `services/core` passed four tests in 53 seconds with zero failures, errors or skips. It covers create and replay, concurrent meeting creation, historical and legacy eligibility, teacher assignment revalidation, and bounded query pages.

## Acceptance

| Criterion | Status |
| --- | --- |
| Typed attendance transitions, audience projections and store contracts | Verified by focused and full tests |
| Direct authorization, eligibility, revision and adapter-contract checks | Verified by focused and full tests |
| Stable receipt replay, meeting uniqueness and lock ordering | Verified by live tests and database review |
| Historical records, omitted markings and student privacy | Verified by live tests |
| Live Attendance tests, full Core test and build, Graphify and independent code and database reviews | Verified on the final candidate |

## Candidate validation

- Comparing `JdbcAttendanceStore` SQL against the starting revision found all 25 fragments identical and in the same order.
- The typed candidate separates the student and staff projections. The PostgreSQL adapter retains current role, assignment, eligibility and record-scope checks. The initial forced Core run recorded 231 passing tests across 45 suites. Final validation includes the operation-type naming and historical receipt assertions.
- The final focused compilation, application tests, receipt adapter contract and live Attendance integration tests passed. The live test compares the full response bytes with the stored receipt and verifies the request hash against a separately serialized baseline draft with two marks. `architectureAudit` reports 56 Core findings and no Attendance findings.
- Final `./gradlew test build --rerun-tasks --console=plain` in `services/core` passed in 6m56s. JUnit XML records 231 tests across 45 suites with zero failures, errors or skips. `graphify update .` refreshed the local graph.
- The scoped whitespace check passed. `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration infra/postgres/demo/core.sql` passed.

## Independent reviews

- The ECC reviewer approved without findings after checking student and staff projections, legacy eligibility, receipt compatibility, authorization order and revision handling.
- The registered PostgreSQL reviewer approved without findings after comparing SQL and binds, the authorization and row locks, the meeting key and revision rules, snapshot pagination, historical eligibility and atomic receipt persistence. The reviewer confirmed all 231 tests passed from the JUnit results.
