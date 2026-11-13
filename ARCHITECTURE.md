# Target architecture

**Status:** required target for future implementation; this document does not describe current compliance or authorize backlog work. The sixteen backend issues remain open. [ARQUITETURA.md](ARQUITETURA.md), the issue acceptance criteria, service contracts, and [docs/backend/operation-ownership.md](docs/backend/operation-ownership.md) remain authoritative for service and data ownership. This document supplies the target code organization and implementation principles; if a conflict appears, those existing contracts win and the discrepancy must be resolved before implementation.

## Service boundaries

| Service | Owns | Persistence and communication |
| --- | --- | --- |
| Core | Identity, sessions, academic authorization and academic capabilities | PostgreSQL `core` schema and role; authenticated private interfaces; transactional outbox publisher to RabbitMQ |
| Questions | Question catalogue and versioned snapshot provider | PostgreSQL `questions` schema and role; calls Core only through authenticated private interfaces |
| Email | Asynchronous delivery worker and its delivery state | PostgreSQL `email` schema and role; consumes Core events from RabbitMQ |

Services run as separate processes. Each service accesses and migrates only its own schema. There is no public gateway, BFF, frontend integration, shared identity schema, cross-schema SQL, or distributed transaction in B-01–B-16. Core is the source of identity and academic authorization. Questions receives only the minimum decision or identity facts required by an authenticated interface. Email does not own identity or public browser endpoints. Existing private operations, event guarantees, credentials, and public routes are defined by `ARQUITETURA.md` and `docs/backend/operation-ownership.md`.

## Code organization

Use feature-oriented packages, with a functional core and explicit adapters. The shape below is a target vocabulary, not a requirement to create empty packages or classes:

```text
<service-root>/<feature>/
  domain/
    ports/in/          # incoming capabilities invoked by controllers/workers
    ports/out/         # persistence, clock, messaging, and remote-service needs
    usecases/          # application orchestration and command/query handling
    models/requests/   # internal use-case inputs
    models/responses/  # internal use-case outputs
    policies/          # deterministic business decisions
    exceptions/        # feature-specific failures
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

The domain package is the pure functional core: it must not import Spring, Jackson, JDBC, HTTP, RabbitMQ, or persistence implementations. Incoming ports define operations the feature offers; use cases implement those ports. Controllers and workers call incoming ports. Outgoing ports define capabilities the use case needs; persistence, messaging, and remote-service adapters implement those ports. Adapters do not call one another. Keep transport DTOs at the transport edge and use-case models inside the application boundary. Keep domain policy independent from framework validation annotations.

Use one named top-level type per matching source file. This includes interfaces, enums, exception/sealed-result subtypes, private helper types, and test support. Split co-located types when extracting them; do not hide DTOs inside controllers or tests inside giant application test files. Place each type under its feature and purpose, and keep the file name equal to the type name. Avoid generic `Utils`, `Models`, `BaseService`, speculative wrappers, empty package trees, and abstractions that do not protect a real boundary.

Share code only for a real cross-feature technical capability with a clear owner. A feature may depend on another feature through a narrow incoming port or an explicit read projection. In Core, a multi-feature read projection may join Core-owned tables within the Core schema when required for a scoped query; document its authorization scope and keep it behind the owning feature's outgoing port. This is not permission for one feature to call another feature's persistence adapter. Do not use shared packages to blur domain ownership.

## Transactions and persistence

Keep the existing Core PostgreSQL schema, JDBC persistence, and SQL contracts unless an authorized issue explicitly changes them. This target does not require a JPA rewrite. Keep authorization filters, pagination, bounded projections, locking, and database constraints in efficient SQL where appropriate; extract deterministic policy and transition rules from persistence code without moving data access into the functional core.

For an atomic use case that spans multiple stores, define a small transaction-running port only when the use case needs to own the atomic boundary. Implement it with Spring `TransactionTemplate` and the service's existing transaction manager. Preserve transaction scope, rollback behavior, lock acquisition order, session revalidation, revision and idempotency checks, and required read locks. Do not hold a database transaction across remote HTTP calls or message delivery. A snapshot fetch happens outside the Core transaction and all required actor, revision, relationship, and idempotency facts are revalidated before commit, as specified by the owning issue.

Core outbox writes stay in the same transaction as the business change. Keep encryption format, key identifiers, associated data, event identity/versioning, confirms, retry, redrive, and at-least-once delivery behavior compatible with [docs/backend/outbox-operations.md](docs/backend/outbox-operations.md). Separate scheduling, use-case decisions, persistence, RabbitMQ publishing, cryptography, and operational HTTP entry points only where the extracted boundaries remain explicit. Never claim exactly-once external email delivery.

## Established libraries and contract safeguards

Prefer maintained libraries already selected by the service or Spring Boot's managed dependency set for common capabilities. Do not write a custom validator, API schema generator, HTTP framework, JSON codec, password hasher, broker client, or transaction manager when an established compatible library already provides it. Verify the exact dependency and configuration against the service's Spring Boot/Kotlin/JDK versions before adding it; architecture guidance alone does not add dependencies.

- **Bean Validation:** for future HTTP input validation, prefer Jakarta Bean Validation with Spring Boot's `spring-boot-starter-validation` and standard constraint annotations, using Kotlin use-site targets such as `@field:NotBlank` where required. Preserve all current observable behavior: missing versus explicit `null` and PATCH semantics, existing validation rules, error body and status, authorization-before-validation ordering where contracted, and validation of JSON parsing failures. Spring MVC validation can raise different exception types for argument validation and method validation; map them deliberately. The contract may require 422 where Spring's default is 400. Annotations supplement, but do not replace, domain policies or validation that must occur after authentication/authorization. Do not add `@Valid` indiscriminately.
- **OpenAPI and Swagger UI:** for the MVC services, use the compatible `org.springdoc:springdoc-openapi-starter-webmvc-ui` line rather than hand-built schema generation or Springfox. The current Core build uses Spring Boot 4.1.2, for which springdoc documents version 3.1.x–3.2.x compatibility; check the exact selected patch against official [springdoc compatibility guidance](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot-) when an authorized implementation adds it. Set generated docs to OpenAPI 3.1. Keep [`docs/http/openapi.json`](docs/http/openapi.json) (3.1, version 1.0.8) as the normative public contract. Generated docs must be checked for operation, schema, security, status, and nullability compatibility and must never overwrite the contract automatically.
- **Architecture verification:** use [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html) as a test dependency for architecture dependency checks. ArchUnit verifies real class/package dependencies, including cycles, and has documented Kotlin support. Use a syntax-aware source check (not bytecode-level ArchUnit rules) for the one-top-level-type-per-file rule because bytecode cannot establish source file ownership for Kotlin. Add ArchUnit only within the authorized refactoring issue (R-09 of the Core refactoring plan or equivalent for other services).

**Native solutions that stay as-is:** the following capabilities are deliberately implemented in project code rather than replaced by generic libraries, because their requirements are specific to the project contracts or are already properly abstracted behind ports:

- `Argon2PasswordHasher` behind `PasswordHasher` port — already properly abstracted with correct algorithm selection.
- `AesGcmOutboxPayloadProtector` behind `OutboxPayloadProtector` port — custom crypto with specific AAD, key-id, and associated-data requirements; no off-the-shelf replacement matches the outbox contract.
- `TransactionRunner` port with `SpringTransactionRunner` — deliberate architectural boundary that keeps `TransactionTemplate` out of the domain layer; not reinventing transaction management.
- `EmailAddress` normalization and validation as a domain value type — richer than annotation-only validation; encapsulates normalization semantics.
- JDBC persistence with explicit SQL — project decision preserving authorization filters, pagination, projections, locking, and constraints in efficient SQL; not a JPA candidate.
- `OriginFilter` — custom origin enforcement matching contracted allowed-Origin behavior.
- Domain policies (scoring, edit rules, access checks, draft transitions) as Kotlin code — these are business logic, not input validation annotations.
- `IdempotencyStore`/`JdbcIdempotencyStore` — specific contract requirements (8–128 character keys, 24-hour replay, permanent receipts for quiz/work submissions).

If springdoc is introduced, make the docs profile explicitly opt-in (for local/test use) and disable the default ungrouped API docs and UI outside that profile. A public Core docs group may be exposed at `/v3/api-docs/core-public` with the UI at `/swagger-ui.html` only in the enabled profile. Exclude every private/internal and operational route from the public group. Group exclusion alone is insufficient: verify every enabled JSON/YAML docs endpoint and UI for route, schema, and secret disclosure, and keep default API docs disabled when the opt-in profile is inactive. Swagger UI must use the existing login flow and `aja_session` cookie behavior; do not add an auth bypass or assume the UI can manually set an HttpOnly cookie. These are technical documentation routes, not new public business operations. Email remains a worker with only private/local operational docs; do not invent a public Email API.

## Verification target

Architecture checks should verify that domain and use-case packages cannot depend on framework, transport, persistence, or broker implementations; adapters depend inward on ports; and adapters do not depend on one another. The recommended tool for these dependency checks is ArchUnit as a test dependency, which verifies real class and package dependencies including cycles. Use a syntax-aware source check for the one-top-level-type-per-file rule because bytecode-level architecture tools cannot establish source file ownership. Keep public contract checks against the normative OpenAPI and exercise any enabled runtime docs routes for accidental exposure.

Choose implementation tools and verification commands only within the authorized issue and B-01 decisions. This target does not imply that any proposed library, package, architecture check, dependency, or runtime docs route exists today.
