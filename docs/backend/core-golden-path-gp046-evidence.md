# GP-04.6 evidence — Announcements application

## Preflight

- Starting revision `bc32ca508b000781847c78358b34e2ff4e428cc7` on `codex/core-golden-path`. The Git index was clean, and the worktree held no Core service edits. Existing documentation and repository changes are outside this issue.
- `graphify query 'AnnouncementController Notice UseCase AnnouncementPersistenceAdapter Calendar Notification outbox AnnouncementsIntegrationTests GP-04.6' --budget 1000` located the controller, calendar use case, and integration tests. The tests include subject transfer visibility without notification replay.
- Scope covers `GET` and `POST /announcements`, `GET` and `PATCH /announcements/{id}`, `POST /announcements/{id}/withdraw`, `GET /calendar`, and the announcement and upcoming-event projections consumed by `GET /me/dashboard`. Core owns announcement writes, recipient scope and the producer transaction. Email owns delivery state.
- Core uses its Java 21 Gradle wrapper and JUnit. Docker Server 29.9.3 has the configured PostgreSQL 16 and RabbitMQ 4 Testcontainers images. The targeted preflight started both containers from the existing integration harness.
- Baseline `./gradlew test --tests '*AnnouncementsIntegrationTests' --console=plain` in `services/core` passed six tests with zero failures, errors or skips in 52 seconds. It covers audience isolation, the RabbitMQ event payload, replay and no-op edits, subject transfer, `notify=false`, calendar deadlines in Brasília time, and rollback on outbox insert failure.
- `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration` passed at preflight.

## Acceptance

| Criterion | Status |
| --- | --- |
| Typed use-case inputs, persistence state and read projections | Verified by focused tests |
| Explicit recipient scope and technical outbox ownership with atomic writes | Verified by focused tests |
| Direct authorization, invariant and port-contract tests | Verified by focused tests |
| Real HTTP, PostgreSQL and RabbitMQ comparison for existing wire, audience and notification behavior | Verified by focused tests |
| Focused tests, full Core test and build, Graphify refresh, independent code and database reviews | Verified on the final candidate |

## Candidate validation

- The new `CoreArchUnitTests.announcement ports use typed application contracts` test failed on the baseline at its application port package assertion. It also checks the transitive port types for generic map and object boundaries.
- Focused `./gradlew test --tests '*AnnouncementsIntegrationTests' --console=plain` passed with PostgreSQL and RabbitMQ. Its create case compares the ordered response fields for a non-null occurrence and checks byte equality between create, replay and `command_idempotency.response_body`.
- The first focused Dashboard run stopped during Spring setup at a refused Testcontainers PostgreSQL connection. The retry of the same Dashboard selector passed all eight tests with zero failures, errors or skips. The Dashboard projection checks still exercise bounded queries and actor scope.
- A SQL comparison found all 30 announcement and outbox SQL fragments preserved after normalizing the event version from the literal `1` to a bind carrying `1`. Bind order, transaction order and rollout behavior are points for database review.
- `infra/postgres/demo/core.sql` includes an all-day occurrence with no `time` key. The baseline announcement response preserves the omission, and PATCH with occurrence omitted rejects that stored shape with 422. The calendar projection independently includes `time: null`. The refactor must distinguish this stored row from an occurrence with an explicit null time.
- The targeted test `AnnouncementsIntegrationTests.announcementWithoutStoredTimeKeepsOmissionAndPatchValidation` failed at the GET assertion with the candidate's time-presence flag forced to true. The same test passed after restoring JSONB key detection, covering the legacy response and both PATCH cases.
- The typed projection has separate with-time and without-time variants. HTTP and Dashboard serialize the variants with their original field shapes. The announcement transaction still protects the announcement, recipient registration, encrypted outbox insert and idempotency receipt. The forced insert failure proves the announcement and recipient counts remain unchanged and no receipt is stored for that operation.
- `graphify update .` refreshed the local graph after the final test edit (5,300 nodes and 14,864 edges). The CLI reported an unavailable optional SQL extractor and unrelated parser warnings. The PostgreSQL queries were inspected from source and exercised through the integration tests.
- The first `./gradlew test build --rerun-tasks --console=plain` run compiled but stopped at `test`. The XML records 222 tests in 41 suites and four failures. Two Assessment tests and one Forum test required a Questions boot JAR. `OutboxIntegrationTests.publisherRepublishesConfirmedEventWhenOutboxMarkingFails` received both RabbitMQ deliveries and failed on the immediate `published_at` assertion. `./gradlew bootJar --console=plain` in `services/questions` built the required artifact in 27 seconds.
- The outbox test now waits for the PostgreSQL publication marker within its existing deadline after receiving the second message. The isolated test passed with identical envelopes, a committed marker and one retry.
- With the Questions jar and bounded marker assertion in place, `./gradlew test build --rerun-tasks --console=plain` in `services/core` passed in 4m45s. JUnit XML records 222 tests across 41 suites, with zero failures, errors or skips. This includes seven Announcements integration tests, eight Dashboard integration tests, four Outbox integration tests, and the direct application and architecture tests.
- `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration infra/postgres/demo/core.sql` and `git diff --check -- services/core` passed on the candidate.
- The separate `./gradlew architectureAudit --console=plain` command exited 1 with 97 Core findings. The report identified two scoped files with multiple primary declarations and new calls from the Outbox producer to the ciphertext type owned by Identity. Those scoped findings require correction and recheck. Remaining Core-wide compliance is tracked through GP-07.
- The correction split the announcement and recipient declarations into their own files and moved `EncryptedOutboxPayload` under its Outbox owner. The architecture audit rerun reports 93 Core findings and no scoped Announcements or Outbox producer findings. The former Identity–Outbox cycle is absent from the report.
- After those corrections, incremental compilation and the focused Announcements, application, and architecture tests passed. Final `./gradlew test build --rerun-tasks --console=plain` in `services/core` passed in 6m12s with 222 tests and zero failures, errors or skips. The final `graphify update .` recorded 5,306 nodes and 14,866 edges.

## Independent reviews

- The independent ECC reviewer approved the first full-gate candidate with no code findings. The reviewer requested the final test command, result and reviews be recorded in this evidence.
- The registered PostgreSQL reviewer, fixed to GPT-6.1 Sol at medium reasoning, approved the first full-gate candidate with no findings. The reviewer verified SQL and bind order, authorization filters and locks, the producer transaction, encryption and publication protocol, legacy occurrence handling and the JUnit totals.
- The same ECC and PostgreSQL reviewers approved the targeted recheck. Both verified the file and package moves preserved projection and payload fields and their order. The PostgreSQL reviewer also reconfirmed the SQL bindings and transaction sequence.
