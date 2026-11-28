# Core golden path guide

This guide describes patterns in the current Core code. The public API remains defined by [the normative OpenAPI document](../http/openapi.json). Service and data boundaries are in [ARCHITECTURE.md](../../ARCHITECTURE.md) and [operation ownership](operation-ownership.md).

## Where code belongs

Use the feature and purpose already responsible for a behavior. Domain models own valid business state and invariants. Domain policies own reusable, deterministic decisions. Application input ports expose capabilities, and use cases coordinate policy, ports, and operation order. Output ports describe mechanisms the application needs. Inbound adapters translate transport input and map failures to HTTP. Outbound adapters implement persistence or remote calls. Configuration wires implementations. A small, one-use decision can be a function in its owning policy or use case. Add a type or abstraction when it names a real state, capability, or boundary.

These are general hexagonal principles. Package names, `InputPort`/`OutputPort`/`UseCase` suffixes, Kotlin declaration placement, and the feature dependency allow-list are AJA conventions. One Core process, JDBC, existing SQL/data shape, and compatibility with the current contract are constraints for this migration. See [the target architecture](../../ARCHITECTURE.md#code-organization) and the [tracked decision matrix](core-golden-path-issues.md#decision-precedence-and-interpretation).

## Examples from the code

### Query: authorize and return a typed projection

[`ListSubjectsUseCase`](../../services/core/src/main/kotlin/br/com/ajadigital/core/curriculum/application/usecases/ListSubjectsUseCase.kt) accepts `ListSubjectsQuery`, applies `CurriculumPolicy` authorization and pagination/sort checks, then asks `SubjectStoreOutputPort` for a `Page<SubjectSummary>`. The application owns authorization order and input normalization. SQL pagination and projection live in the curriculum persistence adapter. The result type is `Page<SubjectSummary>`.

### Atomic application write and outbox

[`CreateAnnouncementUseCase`](../../services/core/src/main/kotlin/br/com/ajadigital/core/announcements/application/usecases/CreateAnnouncementUseCase.kt) runs writer authorization, validation, store authorization, fingerprinting, idempotent replay, creation, and receipt persistence inside `TransactionRunnerOutputPort`. The application defines the sequence and atomic boundary. The JDBC adapter owns SQL, locks, and persistence mechanics. The outbox-producing write stays atomic with the announcement. Keep remote calls and message delivery outside database transactions. See [announcement evidence](core-golden-path-gp046-evidence.md) and [cross-feature transaction records](core-golden-path-issues.md#cross-feature-writetransaction-protocols).

### Remote Questions call before the final write

[`CreateTopicUseCase`](../../services/core/src/main/kotlin/br/com/ajadigital/core/forum/application/usecases/CreateTopicUseCase.kt) checks writer authorization and the replay result through `TopicStoreOutputPort.authorizeAndFindReplay`. For a question target with no replay, it requests and validates the snapshot through `ForumQuestionSnapshotProviderOutputPort`, then calls `TopicStoreOutputPort.create`. The Questions call occurs outside Core's write transaction. The store's guarded write revalidates authorization and current state before persistence. See [Forum evidence](core-golden-path-gp047-evidence.md) and the [private Questions contract](operation-ownership.md#interfaces-privadas-entre-serviços-novas-não-públicas).

### Policy, state, input, result, and errors

[`AcademicPolicy`](../../services/core/src/main/kotlin/br/com/ajadigital/core/academic/domain/policies/AcademicPolicy.kt) makes authorization decisions from actor facts. Keep policies deterministic and free of ports or I/O. An invariant intrinsic to one value belongs in that value's construction or transition methods. [`NewMaterial`](../../services/core/src/main/kotlin/br/com/ajadigital/core/materials/domain/models/NewMaterial.kt) validates its title, duration, content, and source-specific facts. Use a policy when a cohesive decision depends on multiple facts or is shared by decisions. Keep workflow and authorization-before-persistence order in the use case. Typed inputs and results are visible in [`CreateAnnouncementInputPort`](../../services/core/src/main/kotlin/br/com/ajadigital/core/announcements/application/ports/in/CreateAnnouncementInputPort.kt), [`CreateAnnouncementCommand`](../../services/core/src/main/kotlin/br/com/ajadigital/core/announcements/application/models/CreateAnnouncementCommand.kt), and [`Announcement`](../../services/core/src/main/kotlin/br/com/ajadigital/core/announcements/application/contracts/Announcement.kt). Prefer direct parameters when a wrapper would only rename a single value.

Feature failures carry domain meaning. [`AnnouncementsExceptionHandler`](../../services/core/src/main/kotlin/br/com/ajadigital/core/announcements/adapters/in/controllers/handlers/AnnouncementsExceptionHandler.kt) maps them to HTTP through [`FeatureHttpErrors`](../../services/core/src/main/kotlin/br/com/ajadigital/core/shared/adapters/in/web/errors/FeatureHttpErrors.kt). Keep status, body, and precedence aligned with the normative contract. Keep `ResponseEntity` and Spring annotations in adapters.

## How to test a boundary

Call an input port directly to prove application authorization and validation order. [`AcademicApplicationUseCaseTests`](../../services/core/src/test/kotlin/br/com/ajadigital/core/academic/AcademicApplicationUseCaseTests.kt) invokes `CreateClassUseCase` with port substitutes and starts no Spring context. The unauthorized-actor test proves rejection before any store call, and the invalid-fields test proves rejection before persistence.

For a persistence output port, share one contract assertion between an in-memory substitute and the real adapter. [`CurriculumStoreContract`](../../services/core/src/test/kotlin/br/com/ajadigital/core/curriculum/CurriculumStoreContract.kt) checks visibility, authorization, reorder conflicts/application, and visit behavior. [`CurriculumApplicationTests`](../../services/core/src/test/kotlin/br/com/ajadigital/core/curriculum/CurriculumApplicationTests.kt) and [`CurriculumIntegrationTests`](../../services/core/src/test/kotlin/br/com/ajadigital/core/curriculum/CurriculumIntegrationTests.kt) invoke the contract for the substitute and PostgreSQL-backed adapter. This establishes shared observable port behavior. SQL constraints, locking, concurrency, and rollback require live database tests.

## Documentation and data edges

Runtime API docs use the opt-in Core `docs` profile. [`application.properties`](../../services/core/src/main/resources/application.properties) disables docs by default. [`application-docs.properties`](../../services/core/src/main/resources/application-docs.properties) enables them, and [`CoreOpenApiDocsConfiguration`](../../services/core/src/main/kotlin/br/com/ajadigital/core/shared/configuration/CoreOpenApiDocsConfiguration.kt) publishes the public Core group from the normative OpenAPI resource. Follow [GP-06 evidence](core-golden-path-gp06-evidence.md) for activation, cookie-based login, origin setup, and exposure checks. Email owns worker and delivery behavior.

Code dependencies and data dependencies are separate. Feature calls cross published input ports/contracts. A scoped read projection may query multiple Core-owned tables through its consuming feature's output port. Record both the allowed code edge and the tables/columns plus authorization scope. Cross-owner writes need a named coordinator and one preserved atomic protocol. Core, Questions, and Email own separate schemas/roles. Use service APIs across schemas and keep writes within their documented transaction boundary. The [ownership/export/data-edge matrices](core-golden-path-issues.md#ownership-exports-and-dependency-matrices) and [transaction matrix](core-golden-path-issues.md#cross-feature-writetransaction-protocols) record the current exceptions and evidence.

## Limits and corrections

Architecture tests establish structural boundaries and selected source/type properties. Focused behavior tests and semantic review establish policy meaning and authorization precedence. SQL comparison and real infrastructure checks establish persistence equivalence, lock ordering, and transaction behavior. Each evidence layer supports its scoped claims.

Evidence-based corrections during migration are recorded in [GP-07.3](core-golden-path-gp073-evidence.md). PSI accepts the pure `URI` constructor and Kotlin `T : Any` bound while rejecting actual I/O and untyped payloads. Compiled dependency inspection exposed feature calls missed by the initial diagnostic. `IdentityEventsTopology` already had `@Configuration`. The separate negative fixture omits `@Configuration` on its host and proves detection of its `@Bean` method. These checks preserve HTTP and data contracts. The [local issue evidence](core-golden-path-issues.md) records the broader decision and migration history.
