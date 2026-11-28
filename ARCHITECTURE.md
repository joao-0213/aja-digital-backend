# Target architecture

**Status:** required target for future implementation; this document does not describe current compliance or authorize backlog work. The sixteen backend issues remain open. This document defines service boundaries, data ownership, target code organization, and implementation principles. The issue acceptance criteria, public API contract, private service contracts, and [docs/backend/operation-ownership.md](docs/backend/operation-ownership.md) remain authoritative for operation-level behavior and ownership; resolve any discrepancy before implementation.

## Service boundaries

| Service | Owns | Persistence and communication |
| --- | --- | --- |
| Core | Identity, sessions, academic authorization, academic capabilities, files, and persisted snapshots | PostgreSQL `core` schema and role; authenticated private interfaces; transactional outbox publisher to RabbitMQ |
| Questions | Question catalogue and private, versioned snapshot provider | PostgreSQL `questions` schema and role; calls Core only through authenticated private interfaces |
| Email | Asynchronous delivery worker and its delivery state | PostgreSQL `email` schema and role; consumes Core events from RabbitMQ |

The schemas may share one PostgreSQL instance, but each service role can access and migrate only its own schema. Services run as separate processes. There is no public gateway, proxy, BFF, frontend integration, administrative UI, shared identity schema, cross-schema SQL, snapshot global store, dual-write, or distributed transaction in B-01–B-16. Core is the source of identity and academic authorization. Questions receives only the minimum decision or identity facts required by an authenticated interface. Email does not own identity or public browser endpoints. Core writes outbox events in the same transaction as the business change and publishes them through RabbitMQ; Email consumes events and deduplicates its own delivery state.

The public API is fixed by [`docs/http/openapi.json`](docs/http/openapi.json) (OpenAPI 3.1, version 1.0.8). Preserve the `aja_session` HttpOnly cookie and its contract attributes, routes, schemas, identifiers, pagination, status codes, response shapes, and error behavior. Do not invent a CSRF header, JWT, or contract changes from generic examples. Authenticated Core↔Questions interfaces and Core→RabbitMQ→Email events are private and are not part of `/api/v1`. The complete operation and ownership matrix is in [docs/backend/operation-ownership.md](docs/backend/operation-ownership.md).

Test services directly against real PostgreSQL and RabbitMQ test instances or equivalent real processes. HTTP mocks alone do not establish service contracts.

## Code organization

Use feature-oriented packages, with separate domain and application layers, and explicit adapters. This is the required target for Core and a reference for future service work; it does not claim Questions or Email have migrated. The shape below is a target vocabulary, not a requirement to create empty packages or classes. B-01 determines the concrete project structure and tools without anticipating undecided conventions:

```text
<service-root>/<feature>/
  domain/
    policies/          # deterministic business decisions
    models/            # business concepts and immutable published values
    exceptions/        # feature-specific failures
  application/
    ports/in/          # capabilities offered by this feature
    ports/out/         # external capabilities required by application
    contracts/         # cross-feature inputs/results explicitly published
    models/            # application states and projections
    usecases/          # operation orchestration
    exceptions/        # application failures, without HTTP status codes
  adapters/
    in/controllers/
      models/requests/ # HTTP request DTOs
      models/responses/# HTTP response DTOs
      mappers/         # HTTP-to-domain and domain-to-HTTP conversion when needed
      handlers/        # transport error mapping
    in/workers/        # message consumers, where applicable
    out/persistence/   # schema-local stores
    out/messaging/     # publishers/consumers as appropriate
    out/<integration>/ # HTTP clients and other external adapters
  configuration/      # framework wiring for the service or feature
```

The domain package contains business concepts and pure decisions; it must not import application, Spring, Jackson, JDBC, HTTP, RabbitMQ, or persistence implementations. The application depends inward on domain concepts and ports, coordinates operations, and does not depend on external implementations. Incoming ports define capabilities offered by a feature; controllers/workers call them. Outgoing ports define capabilities the application needs; adapters implement them. Adapters do not call one another. Keep transport DTOs at the transport edge. Keep domain policy independent from framework validation annotations.

Name incoming port interfaces with the `InputPort` suffix, outgoing port interfaces with the `OutputPort` suffix, and concrete use-case classes with the `UseCase` suffix. Ports live in `application/ports/{in,out}/`; use cases live in `application/usecases/`. Use `Command` for mutations, `Query` for composite queries, and semantic result names. An operation may accept no arguments, a simple value, or one cohesive object; do not wrap a command solely to satisfy a signature. Reserve `Dto` for actual transport/data transfer types. Domain entities and values keep their domain names.

Architectural principles are inward dependencies, transport-independent authorization and invariants, explicit ownership, and replaceable external mechanisms with behavioral contracts. Feature packages, port suffixes, file organization, and verification tools are AJA conventions, not universal hexagonal requirements. One Core process/project with JDBC and no schema changes is a migration constraint, not a general architecture rule. Core feature modules are not automatically DDD bounded contexts or future services.

Do not use generic `Support`, `Helper`, `Utils`, `Base`, or `Generic` types as a home for mixed responsibilities. Keep pure decisions in focused feature policies, HTTP/request mapping in inbound adapter mappers, and orchestration in the use case that owns it. A small feature-local helper is acceptable only when its name and package state one cohesive responsibility.

Use one primary declaration per file, with a matching filename. Closely related sealed subtypes, small results, extensions, and private cohesive helpers may remain in that file; do not split them solely to enforce one declaration per file. Avoid unrelated declaration bundles, DTOs hidden inside controllers, and tests collected in giant application test files. Place each type under its feature and purpose. Avoid generic `Utils`, `Models`, `BaseService`, speculative wrappers, empty package trees, and abstractions that do not protect a real boundary.

Share code only for a real cross-feature technical capability with a clear owner. Between features, allow only another feature's incoming port and contracts in `application/contracts`; immutable domain values may cross only when individually enumerated as published exports. Persistence states, outgoing ports, use cases, adapters, and configuration remain internal. Keep the feature dependency graph acyclic and record each allowed edge with consumer, capability, justification, and test. A multi-feature read projection may join Core-owned tables for a scoped query; record its table/column dependencies, authorization scope, and query test separately from code dependencies, behind the consuming feature's port. It stays read-only and grants no write ownership. Do not use shared packages to blur domain ownership.

## Transactions and persistence

Keep the existing Core PostgreSQL schema, JDBC persistence, and SQL contracts unless an authorized issue explicitly changes them. This target does not require a JPA rewrite. Keep authorization filters, pagination, bounded projections, locking, and database constraints in efficient SQL where appropriate; extract deterministic policy and transition rules from persistence code without moving data access into the functional core.

For an atomic use case that spans multiple stores, define a small transaction-running port only when the use case needs to own the atomic boundary. Implement it with Spring `TransactionTemplate` and the service's existing transaction manager. Preserve transaction scope, rollback behavior, lock acquisition order, session revalidation, revision and idempotency checks, and required read locks. Do not hold a database transaction across remote HTTP calls or message delivery. A snapshot fetch happens outside the Core transaction and all required actor, revision, relationship, and idempotency facts are revalidated before commit, as specified by the owning issue.

Core outbox writes stay in the same transaction as the business change. Keep encryption format, key identifiers, associated data, event identity/versioning, confirms, retry, redrive, and at-least-once delivery behavior compatible with [docs/backend/outbox-operations.md](docs/backend/outbox-operations.md). Separate scheduling, use-case decisions, persistence, RabbitMQ publishing, cryptography, and operational HTTP entry points only where the extracted boundaries remain explicit. Never claim exactly-once external email delivery.

## Established libraries and contract safeguards

Prefer maintained libraries already selected by the service or Spring Boot's managed dependency set for common capabilities. Do not write a custom validator, API schema generator, HTTP framework, JSON codec, password hasher, broker client, or transaction manager when an established compatible library already provides it. Verify the exact dependency and configuration against the service's Spring Boot/Kotlin/JDK versions before adding it; architecture guidance alone does not add dependencies.

- **Bean Validation:** for future HTTP input validation, prefer Jakarta Bean Validation with Spring Boot's `spring-boot-starter-validation` and standard constraint annotations, using Kotlin use-site targets such as `@field:NotBlank` where required. Preserve all current observable behavior: missing versus explicit `null` and PATCH semantics, existing validation rules, error body and status, authorization-before-validation ordering where contracted, and validation of JSON parsing failures. Spring MVC validation can raise different exception types for argument validation and method validation; map them deliberately. The contract may require 422 where Spring's default is 400. Annotations supplement, but do not replace, domain policies or validation that must occur after authentication/authorization. Do not add `@Valid` indiscriminately.
- **OpenAPI and Swagger UI:** for the MVC services, use the compatible `org.springdoc:springdoc-openapi-starter-webmvc-ui` line rather than hand-built schema generation or Springfox. The current Core build uses Spring Boot 4.1.2, for which springdoc documents version 3.1.x–3.2.x compatibility; check the exact selected patch against official [springdoc compatibility guidance](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot-) when an authorized implementation adds it. Set generated docs to OpenAPI 3.1. Keep [`docs/http/openapi.json`](docs/http/openapi.json) (3.1, version 1.0.8) as the normative public contract. Generated docs must be checked for operation, schema, security, status, and nullability compatibility and must never overwrite the contract automatically.
- **Architecture verification:** use [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html) as a test dependency for architecture dependency checks. ArchUnit verifies real class/package dependencies, including cycles, and has documented Kotlin support. Use bounded Kotlin PSI checks for declaration/file structure because bytecode cannot establish source filename ownership. Keep the one-primary-declaration convention and its related-declaration allowance above; do not enforce a one-type-per-file split. Add ArchUnit only within the authorized refactoring issue (R-09 of the Core refactoring plan or equivalent for other services).

**Native solutions that stay as-is:** the following capabilities are deliberately implemented in project code rather than replaced by generic libraries, because their requirements are specific to the project contracts or are already properly abstracted behind ports:

- `Argon2PasswordHasher` behind `PasswordHasher` port — already properly abstracted with correct algorithm selection.
- `AesGcmOutboxPayloadProtector` behind `OutboxPayloadProtector` port — custom crypto with specific AAD, key-id, and associated-data requirements; no off-the-shelf replacement matches the outbox contract.
- `TransactionRunner` port with `SpringTransactionRunner` — deliberate architectural boundary that keeps `TransactionTemplate` out of the domain layer; not reinventing transaction management.
- `EmailAddress` is Identity's published, framework-free validator for the email formats already accepted by Identity and Academic.
- JDBC persistence with explicit SQL — project decision preserving authorization filters, pagination, projections, locking, and constraints in efficient SQL; not a JPA candidate.
- `OriginFilter` — custom origin enforcement matching contracted allowed-Origin behavior.
- Domain policies (scoring, edit rules, access checks, draft transitions) as Kotlin code — these are business logic, not input validation annotations.
- `IdempotencyStore`/`JdbcIdempotencyStore` — specific contract requirements (8–128 character keys, 24-hour replay, permanent receipts for quiz/work submissions).

If springdoc is introduced, make the docs profile explicitly opt-in (for local/test use) and disable the default ungrouped API docs and UI outside that profile. A public Core docs group may be exposed at `/v3/api-docs/core-public` with the UI at `/swagger-ui.html` only in the enabled profile. Exclude every private/internal and operational route from the public group. Group exclusion alone is insufficient: verify every enabled JSON/YAML docs endpoint and UI for route, schema, and secret disclosure, and keep default API docs disabled when the opt-in profile is inactive. Swagger UI must use the existing login flow and `aja_session` cookie behavior; do not add an auth bypass or assume the UI can manually set an HttpOnly cookie. These are technical documentation routes, not new public business operations. Email remains a worker with only private/local operational docs; do not invent a public Email API.

## Verification target

Core runs its production architecture checks through `test` and `check`. `architectureAudit` also runs them on its own and writes the findings report. The guide links the implementation examples and verification evidence. [Core golden path guide](docs/backend/core-golden-path-guide.md)

Architecture checks should verify that domain and application packages cannot depend on framework, transport, persistence, or broker implementations; adapters depend inward on ports; and adapters do not depend on one another. The recommended tool for these dependency checks is ArchUnit as a test dependency, which verifies real class and package dependencies including cycles. Use bounded Kotlin PSI source checks for the primary declaration, filename, and allowed related declarations; do not require every helper or subtype to be split into its own file. Keep public contract checks against the normative OpenAPI and exercise any enabled runtime docs routes for accidental exposure.

Choose implementation tools and verification commands only within the authorized issue and B-01 decisions. This target does not imply that any proposed library, package, architecture check, dependency, or runtime docs route exists today.

## Documentation sources and limits

- [`ISSUES-BACKEND.md`](ISSUES-BACKEND.md) is the implementation backlog; all sixteen issues remain open.
- [`docs/http/`](docs/http/) records observable contract behavior and its provenance. JSON Server and local-storage examples are evidence, not requirements to reproduce the frontend simulator's storage.
- [`docs/reference/frontend-sources.md`](docs/reference/frontend-sources.md) identifies historical external sources. Frontend suites are references, never evidence of backend implementation.
- `services/*/README.md` describes planned scope only; it does not prove a service or feature is implemented.
