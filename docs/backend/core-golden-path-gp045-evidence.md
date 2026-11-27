# GP-04.5 evidence — Submissions and feedback

Starting revision `dc9628eb80f99373b2f65629c4960b85ce71b2e2` on `codex/core-golden-path`. The Core tree and Git index were clean. Pre-existing repository documentation edits remain in the checkout. GP-03 and GP-04.2 are committed dependencies.

## Preflight

- `graphify query 'JdbcSubmissionStore CreateSubmissionUseCase UpdateSubmissionUseCase SubmitSubmissionUseCase SubmissionPolicy FeedbackOutputPort' --budget 800` identified the store, workflow, policy, configuration and application boundaries.
- The OpenAPI defines seven submission and feedback operations. The HTTP contract distinguishes student and staff responses and specifies attachment scope, confirmation, receipts, and the separate feedback draft and publication workflows. Dashboard also uses the authorized pending-feedback count.
- The previous stage's `gradlew.bat test build --rerun-tasks --console=plain` passed 212 Core tests across 38 suites at the starting code state. The suite uses PostgreSQL and RabbitMQ via Testcontainers. Existing integration cases exercise attached-file expiry and deadline checks under locks, live receipt recovery, authorized uploads and downloads, and feedback publication.
- The starting `JdbcSubmissionStore` contains 22 SQL statement literals, eight transaction annotations (three read-only), five `FOR UPDATE` clauses, one `FOR SHARE` clause and one advisory lock expression.

## Acceptance

| Criterion | State |
| --- | --- |
| Typed preparation, submission, and feedback publication state and ports | Verified by the mandatory architecture test, immutable boundary tests and reviews |
| Direct authorization and transition tests | Verified through the input ports, including invalid attachment cases |
| Attachment ownership, limits, atomic association and real file/store contracts | Verified with PostgreSQL and live Core HTTP |
| Role-specific feedback visibility and compatibility of historical receipts | Verified by real HTTP and byte comparison against stored receipts |
| Focused HTTP, full Core test/build, Graphify refresh and independent reviews | Verified. Core-wide architecture findings remain tracked under GP-07 |

## Boundaries

- Submission application ports use operation-specific commands, queries, and persistence records. The typed read model distinguishes a student preparation, a student receipt, and a completed submission visible to staff. The staff view can hold a new draft alongside prior published feedback and its history.
- Existing PostgreSQL protocols still protect file ownership, staged status, expiry, deadline, assignment, feedback grades and revision. The submission flow associates the staged files atomically with the receipt. Dashboard reads its count through the assessment input port.
- The controller derives request hashes from the existing serialized request values. The storage and HTTP boundaries retain the nullable fields and numeric values in legacy feedback and receipt documents.

## Validation

- Architecture RED `gradlew.bat test --tests '*CoreArchUnitTests.submission ports use typed application contracts' --console=plain` ran one test and failed because submission ports expose `java.lang.Object` through raw maps and `Any`.
- Incremental `gradlew.bat compileKotlin compileTestKotlin --console=plain` exited 0. The expanded GREEN selector `gradlew.bat test --tests '*CoreArchUnitTests.submission ports use typed application contracts' --tests '*SubmissionUseCaseTests' --console=plain` passed four tests, including authorization and projection checks through the typed ports.
- After moving the persistence parameters and renaming the commands and queries, incremental compilation passed again. The same GREEN selector passed five tests, adding direct rejection coverage for duplicate files and credential-bearing URLs.
- A source comparison against `dc9628e` found all 22 SQL literals and the same transaction and lock expressions. `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration` exited 0.
- An initial real assessment integration run passed 26 of 27 tests. The new staff list test mistakenly expected the `feedbackDraft` key to be absent. The starting response mapper includes the key with a null value for staff and omits it for students. The assertion was corrected before the candidate rerun.
- The corrected `gradlew.bat test --tests '*AssessmentIntegrationTests' --console=plain` run passed all 27 tests with no failures, errors or skips. The test class ran in 390.099 seconds. Tests exercised the real HTTP submission list and replay equality for preparation, submission and feedback publication.
- `graphify update .` exited 0. The refreshed graph locates `SubmissionView`, `SubmissionController` and the relocated application store port. It was refreshed again after the final package moves.
- The first final `gradlew.bat test build --rerun-tasks --console=plain` run recorded 217 tests across 39 suites with two failures. The new explicit-null attachment assertion incorrectly expected 400; the starting policy rejected null with `IllegalArgumentException` and the shared handler maps it to 422. The restart receipt test timed out while waiting for the fresh Core process after its database and JPA initialization. Source comparison also found a real PATCH regression: the adapter incorrectly required `attachments` even when the attachment selection was unchanged. The correction must distinguish an omitted value from explicit null and enforce the original strict types.
- After restoring strict, role-ordered request decoding and historical wire order, `gradlew.bat test --tests '*AssessmentIntegrationTests.submissionDraftCanBeCreatedForAnAssignedPublishedWork' --console=plain` passed. The real HTTP case verifies PATCH omission retains attachments, explicit null and a string revision return 422, and the HTTP bodies for preparation, submission and feedback publication equal the stored `response_body` documents byte for byte.
- `gradlew.bat test --tests '*AssessmentIntegrationTests.submissionReceiptSurvivesFreshCoreProcessesAndExpiredIdempotencyRecord' --console=plain` exited 0 in 2m20s, one test passed. The isolated run used the existing 60-second readiness deadline and reproduced recovery of the permanent receipt after the idempotency record expired.
- The separate Dashboard selector `gradlew.bat test --tests '*DashboardIntegrationTests.staffDashboardCountsAllPendingFeedbackAndOnlyShowsPublishedAssessments' --console=plain` exited 0 in 1m1s.
- Pre-review `gradlew.bat test build --rerun-tasks --console=plain` exited 0 in 6m50s. The XML records 218 tests across 39 suites with zero failures, errors or skips. It includes 28 assessment integration tests and eight Dashboard integration tests. The fresh-process case passed on its existing 60-second deadline.
- `gradlew.bat architectureAudit --console=plain` exited 1 in 18s with 114 Core-wide findings, down from 132 in GP-04.4. The only reported finding in the touched submission or attempt classes is the prior `java.net.URI` import in the pure link validator. The mandatory submission contract rule passes. Core-wide compliance remains tracked under GP-07.

## Reviews

- Independent ECC found two D-04 gaps in the pre-review candidate. `SubmissionPage` retained the caller's list, and `SubmissionView.Feedback` accepted arbitrary mutable `Number` values. The targeted corrections must keep the existing `Long` total, number serialization and stored receipt compatibility.
- The registered database reviewer approved the pre-review candidate with no confirmed findings. The reviewer independently compared the ordered SQL literals, binds, locks and original access and expiry checks, and counted 218 passing tests in the XML artifacts. The registered role uses GPT-6.1 Sol at medium reasoning, as recorded in GP-04.4.
- The post-review candidate snapshots `SubmissionPage.items`, keeps a `Long` total and uses immutable numeric grade variants. `gradlew.bat test --tests '*CoreArchUnitTests.submission ports use typed application contracts' --tests '*SubmissionUseCaseTests' --tests '*AssessmentIntegrationTests.submissionDraftCanBeCreatedForAnAssignedPublishedWork' --console=plain` passed six tests. The real HTTP case includes a small fractional grade and byte equality against the stored receipt. `graphify update .` exited 0 after these corrections.
- ECC resolved the grade and collection-alias findings. The same reviewer asked to remove a new comparison between the page's total and its item count. Under PostgreSQL `READ COMMITTED`, the count and row reads use separate statements. The DTO should keep the nonnegative total without adding a dependency between snapshots. The database reviewer found no changes in the current SQL or lock protocol and approved the grade codec and receipt preservation.
- The final pagination correction retained only a nonnegative total alongside the bounds on the page number, size and item list. A direct regression test permits one listed row with an independent total of zero. Repeating the targeted six-test command passed in 1m15s. The obsolete forced run was stopped before completing its test task.
- The same ECC and database reviewers approved the revised candidate. The targeted rechecks confirmed that immutable grades and pages retain the original receipt numbers and independent count semantics. The full test/build gate remained necessary for closure.
- Final `gradlew.bat test build --rerun-tasks --console=plain` exited 0 in 7m31s. XML records 218 tests across 39 suites, with zero failures, errors or skips. The separate `gradlew.bat architectureAudit --console=plain` exited 1 in 19.8s on the same 114 Core-wide findings. `graphify update .` exited 0 after the final pagination correction.
