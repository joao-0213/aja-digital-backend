# GP-01 evidence — assessment confirmation null contract

**Status: complete.** Starting HEAD was `901bbf053a37791326f9c22af3437a11a74fd0a1`. The checkout already had broad unrelated changes; the GP-01 production change is limited to `AssessmentPolicy.optionalBoolean`, with focused method-level and HTTP/PostgreSQL tests.

Preflight: `& 'C:\Users\joaov\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe' ps --format '{{.Names}} {{.Status}} {{.Ports}}'` showed `aja-b03-acceptance-postgres-1` and `aja-b03-acceptance-rabbitmq-1` healthy. Testcontainers detection was subsequently confirmed by the focused RED run starting `testcontainers/ryuk:0.14.0`; the HTTP/PostgreSQL test reached the running Core process and database. JDK 21 was present at the required path.

## Contract and behavior

The normative `AssessmentUpdate` schema in `docs/http/openapi.json` declares `confirmResetDrafts` as an optional boolean and does not include `null` in its type. The HTTP contract requires this flag before changing questions when existing drafts need reset; only unanswered drafts are reset and submitted attempts are preserved. Absence means false; explicit `null` and all non-boolean values are invalid. The fix preserves the request hash input, idempotency key handling, authorization order, persistence adapter, and transaction boundaries.

## RED/GREEN evidence

All commands ran from `services/core` with `JAVA_HOME=C:\Users\joaov\.jdks\azul-21.0.13.0.3`.

| Command | Outcome |
| --- | --- |
| `gradlew.bat test --tests '*UpdateAssessmentUseCaseTests' --tests '*AssessmentIntegrationTests.assessmentPatchDistinguishesAbsentConfirmationFromExplicitNullOverHttpAndPostgres' --console=plain` before the production fix | **RED**, exit 1; 4 tests, 2 failures. Method-level test expected `IllegalArgumentException` for explicit null but none was thrown. Real HTTP/PostgreSQL PATCH expected 422 but returned 200 and response revision 2. The fixture confirms a write occurred where invalid input must be rejected. |
| Same focused command after the fix | **GREEN**, exit 0; 4 tests passed. Method-level cases cover absence, explicit null, false, true, string, number, array and object. The student/null case proves authorization returns 403 before confirmation-type validation. |
| `gradlew.bat test --tests '*AssessmentIntegrationTests' --console=plain` | Exit 0; all 24 assessment integration tests passed against the Testcontainers-backed PostgreSQL and RabbitMQ setup. |
| `gradlew.bat test --rerun-tasks --console=plain` | Exit 1; 166 tests completed, 3 failures outside GP-01. `PlatformIntegrationTests.springTransactionRunnerRollsBackAtomicallyOnFailure`: expected 0, got 1. `CurriculumIntegrationTests.concurrentAdminDeactivationCannotRemoveTheLastAdministrator`: expected statuses `[200, 409]`, got `[200, 500]`. `ForumIntegrationTests.questionReferenceUsesAuthenticatedSnapshotHttpProviderInFreshCoreProcess`: fresh Core process did not become healthy. The focused assessment suite and the new test passed in this run. |
| `gradlew.bat build --rerun-tasks -x test --console=plain` | Exit 0; forced non-test build tasks including compilation, `bootJar`, `jar`, `assemble`, and `build` passed. Full `build --rerun-tasks` was not repeated because it would rerun the already-failing full test suite without a new hypothesis. |
| `graphify update .` | Completed after source changes and review-test refinement; ignored graph refreshed to 4,267 nodes / 12,170 edges. Existing unrelated parser/SQL extraction warnings are recorded in the console output. |
| `git diff --check` | Exit 0; no whitespace errors. Git printed existing worktree LF-to-CRLF warnings for dirty unrelated files. |

The HTTP regression test also verifies PATCH accepts nullable `deadline: null`, rejects missing required `expectedRevision` with 422, and returns 403 for a student submitting malformed confirmation. The direct method test covers invalid JSON-compatible value types. Existing endpoint/session tests remain responsible for other 401/404/409 precedence; this change does not alter those paths.

## Implementation

`AssessmentPolicy.optionalBoolean` now checks `containsKey` before reading the value. It returns `null` only when the property is absent and requires a present value to be a Boolean. This keeps the existing default false behavior for absence while rejecting explicit JSON null.

No SQL, migration, persistence, or transaction code changed, so a database-specific code review was not applicable. ECC and Ponytail initially found the same nullable-removal fixture gap; after the non-null-to-null correction and focused four-test rerun, the same reviewers approved with zero remaining findings.
