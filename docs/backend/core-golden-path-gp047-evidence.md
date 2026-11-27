# GP-04.7 evidence — Forum application

## Preflight

- Starting revision `9d582314c7bd442a459fb8b409a6ce5d5be51a68` on `codex/core-golden-path`. The Git index and Core service tree were clean. Existing documentation and repository changes are outside this issue.
- `graphify query 'ForumController ForumIntegrationTests CreateForumTopicUseCase JdbcForumStore Questions snapshot Forum dashboard GP-04.7' --budget 850` located the Forum HTTP entry point, live integration suite and Dashboard consumer.
- Scope covers `GET` and `POST /topics`, `GET /topics/{id}`, reply, read, solution, vote and withdrawal operations, and the Forum summaries consumed by Dashboard. Core owns Forum state and authorization. References to Questions use the authenticated private snapshot endpoint. The Forum write transaction revalidates local state after the snapshot request.
- Java 21 and the checked-in Gradle wrapper are available. Docker 29.9.3 runs the existing PostgreSQL and RabbitMQ Testcontainers images. The Questions boot JAR built during GP-04.6 is available for tests that start the real private service.
- Baseline `./gradlew test --tests '*ForumIntegrationTests' --console=plain` in `services/core` passed all 16 integration tests in 1m49s with zero failures, errors or skips. It exercised concurrent replies, read watermarks, attachment and material scope, authorization rechecks after waiting, and a real Questions process.

## Acceptance

| Criterion | Status |
| --- | --- |
| Typed topic and reply states, port boundaries and bounded Dashboard summaries | Verified by focused and full tests |
| Direct authorization, state and adapter-contract checks | Verified by focused and full tests |
| Attachment authorization, receipt replay, lock order and concurrent replies | Verified by live PostgreSQL and HTTP tests |
| Authenticated Questions snapshot before the Core write transaction and local revalidation at commit | Verified by live Forum tests |
| Live Forum tests, full Core test and build, Graphify and independent code and database reviews | Verified on the final candidate |

## Candidate validation

- The new `CoreArchUnitTests.forum ports use typed application contracts` test ran against the baseline and failed at the expected port inventory assertion. The Forum-owned snapshot port was absent and the topic store port lived under `domain.ports.out`. Existing Forum ports also exposed generic response maps.
- The first candidate Forum integration run passed 14 of 16 tests. Both failures timed out waiting for the test snapshot provider to signal its concurrency gate. The Forum-port overload now signals and waits on the same fixture gate as the Assessment-port overload.
- With the gate restored and the typed JDBC mappings in place, `./gradlew test --tests '*ForumIntegrationTests' --console=plain` passed all 16 tests. The new assertions compare the create and reply HTTP responses with their replays and stored idempotency documents.
- Comparing the ordered SQL fragments in `JdbcTopicStore` against the starting revision found all 41 fragments identical. The independent database review also checks their bind order and transaction call sequence.
- Incremental compilation and the focused architecture, direct application, HTTP adapter, and transaction tests passed after moving the final published contracts. The JUnit results record one architecture test, four application tests, two HTTP adapter tests and two transaction tests. The separate `architectureAudit` reports 69 findings across Core and no scoped Forum or Questions snapshot findings.
- The final layout keeps receipt serialization in the persistence adapter and the HTTP JSON ordering in an inbound Jackson mixin. The targeted HTTP create and reply receipt test, all 16 Forum integration tests and all eight Dashboard integration tests passed in serialized runs during candidate validation.
- Final `./gradlew test build --rerun-tasks --console=plain` in `services/core` passed in 7m8s. JUnit XML records 229 tests across 43 suites with zero failures, errors or skips. The final `graphify update .` refreshed 5,415 nodes and 15,190 edges.
- A separate `./gradlew architectureAudit --console=plain` rerun on the final revision exited 1 and recorded 69 Core findings, with no Forum or Questions snapshot findings. The remaining architecture work is tracked through GP-07.
- `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration infra/postgres/demo/core.sql` and the scoped whitespace check passed on the candidate.

## Independent reviews

- The ECC reviewer approved without findings after reviewing the typed projections, receipt codecs, hashes, authorization and lock order, and the authenticated Questions snapshot boundary.
- The registered PostgreSQL reviewer approved without findings after checking ordered SQL, bindings, transaction and authorization order, material and attachment scope, read watermarks and the existing constraints. The reviewer confirmed all 229 tests passed from the JUnit results.
