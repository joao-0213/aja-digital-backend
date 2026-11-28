# Execution prompt — Core as the golden path

**Architectural decisions established on November 17, 2026.** This file provides instructions for the agent authorized by the user to execute them. Creating this file did not start the refactoring and does not prove implementation. The criteria below define the required outcome.

## 1. Mission, authorization, and boundaries

When the user provides this document as an execution instruction, complete the refactoring of `services/core/` as the reference for the other services. First create an executable plan as local issues, then proceed automatically to execution. Do not stop after delivering the plan or request authorization again for each issue.

Decisions D-01 through D-12 are the implementation baseline. Apply them without reopening choices based on personal preference. A demonstrated architectural defect, such as business logic trapped in an adapter, an ownership contradiction, or an untestable boundary, also warrants correction even when HTTP behavior is unchanged. Record the evidence, affected decision, smallest correction, alternatives rejected, and acceptance tests in the execution record before proceeding. Resolve corrections within the authorized scope autonomously; block only work that requires changing functional contracts or expanding scope. Do not invent product requirements.

Authorized scope: Core, tests, necessary build/configuration changes, documentation, and directly affected verification scripts. This includes the outstanding R-07, R-08, and R-09 work in the [existing plan](core-refactoring-plan.md), corrections to drift after R-00–R-06, and the confirmed findings in this document.

Do not refactor Questions or Email or implement B-01–B-16 features. Run those services as dependencies when required by existing tests. Do not introduce a gateway, BFF, frontend, new services, schemas, migrations, or a switch to JPA. Do not push, deploy, or publish issues to external services.

Read [AGENTS.md](../../AGENTS.md), [ARCHITECTURE.md](../../ARCHITECTURE.md), [ownership](operation-ownership.md), [OpenAPI](../http/openapi.json), [outbox operations](outbox-operations.md), the plan, and its evidence. Preserve public and private contracts, the `aja_session` cookie, status codes, headers, formats, identifiers, pagination, error precedence, authorization, snapshots, idempotency, and atomicity.

This mandate supersedes, **for the Core refactoring**, incompatible older conventions: a mandatory DTO per method, a mandatory file for every helper type, and mandatory placement of use cases inside `domain`. It permits fixing regressions proven against the contract, despite the previous prohibition on any logic changes. It does not change functional requirements or authorize refactoring the other services. Document this precedence before moving code.

## 2. Mandatory architectural decisions

Distinguish three categories when implementing and documenting these decisions:

- **Architectural principles:** inward dependencies, transport-independent authorization and invariants, explicit ownership, and replaceable external mechanisms with behavioral contracts.
- **AJA conventions:** feature packages, port suffixes, file organization, and the selected verification tools. Follow these for consistency; do not present them as universal requirements of hexagonal architecture.
- **Migration constraints:** one Core process/Gradle project, existing JDBC/SQL, no schema changes, and compatibility with existing contracts. These bound this execution; they do not define hexagonal architecture for every future system.

### D-01 — Feature-oriented organization with separate domain and application layers

Keep a single Gradle project and a single Core process. Do not create build modules, an internal framework, a command bus, event sourcing, or new infrastructure.

Use the following structure, creating only directories that have content:

```text
<feature>/
  domain/
    models/                 # business entities, values, and facts
    policies/               # pure decisions
    exceptions/             # invariant violations
  application/
    ports/in/               # offered capabilities
    ports/out/              # required external capabilities
    contracts/              # commands, queries, and results exposed within the service
    models/                 # private states/projections used by the application and its ports
    usecases/               # operation coordination
    exceptions/             # application failures, without HTTP status codes
  adapters/
    in/controllers/         # HTTP and its models, mappers, and handlers
    in/filters/             # when owned by the feature
    in/scheduling/          # scheduling
    out/persistence/        # JDBC
    out/<integration>/      # remote HTTP, messaging, security, etc.
  configuration/            # Spring composition
```

`domain` does not depend on `application`, adapters, configuration, Spring, Jackson, JDBC, HTTP, or the broker. `application` depends on the domain, contracts, and ports, never on external implementations. Adapter dependencies point inward. Configuration may know implementations to wire them together, without business rules. Keep `CoreApplication` at the service root for Spring discovery.

This separation distinguishes business decisions from coordination with side effects. Moving classes without separating their contents does not satisfy this decision.

### D-02 — Ownership and boundaries between features

Keep `identity`, `curriculum`, `materials`, `assessment`, `announcements`, `forum`, `attendance`, `dashboard`, `outbox`, and `health`. Rename `classroom` to **`academic`**, because it includes people, academic roles, classes, students, and enrollments. Organize people, class, and enrollment management capabilities within this context, without creating new services or distributed transactions.

`identity` owns credentials, sessions, confirmation, recovery, and the minimal representation of the authenticated actor. `academic` owns academic management and relationships. Enrollments changed atomically by identity confirmation remain within the same Core SQL protocol; this split does not authorize breaking their atomicity or creating circular calls between features.

`assessment` keeps authoring, attempts, and submissions in the same context, with separate capabilities and stores. `materials` keeps files, materials, and progress. `dashboard` composes reads; no other feature depends on it. `outbox` does not depend on the features that produce events. `shared` does not depend on any feature.

These features are internal modules of Core, not automatically separate DDD bounded contexts or future microservices. Treat references to a feature's context as its module scope unless a distinct domain language/model boundary is documented. Do not manufacture separate databases or distributed workflows to enforce package boundaries.

Extend the ownership matrix with each data set's business owner, permitted writers, cross-feature readers, and each cross-feature transaction's application coordinator, invariants, and atomic write port. Identity confirmation is coordinated by the identity application capability; its existing atomic enrollment updates are an explicitly recorded cross-owner protocol, not general permission for identity adapters to manage academic data. Other cross-owner writes require the same operation-specific ownership record. Preserve the protocol without introducing circular feature calls.

Between features, allow only incoming ports and their contracts in `application/contracts`. Domain models may cross this boundary only as immutable values explicitly published and enumerated in the export matrix; do not expose entire domain trees. Persistence states, outgoing ports, concrete use cases, adapters, and configuration are internal to their owner.

Convert `AcademicActor` into a minimal identity contract consumed by the application. Policies receive the necessary authorization facts without depending on session objects. Do not expose `SessionRecord`, passwords, tokens, or raw cookies as generic results between features or over HTTP. Internal session references needed for revalidation must stay out of logs, responses, and sensitive `toString` output.

The dependency graph between features must be acyclic. The executor must record the edges actually required, each with its consumer, public capability, justification, and test; an edge absent from the list is prohibited. This is an inventory of how these boundaries are implemented, not authorization to add arbitrary relationships. Do not resolve cycles by moving rules into `shared`.

Read joins across Core tables remain allowed in typed projections behind the consuming feature's port, with explicit authorization and limits. Do not extract SQL into internal endpoints between features in the same process merely to avoid joins. Do not add code dependencies between features just because a query uses their tables.

Those joins still create data dependencies. Record the tables/columns read, owning and consuming modules, authorization scope, and affected query tests separately from the code dependency graph. ArchUnit cannot establish SQL ownership or data independence from imports. Keep these exceptions read-only; ownership of a projection does not confer write ownership of its source tables.

### D-03 — Ports and names express capabilities

Keep the `InputPort`, `OutputPort`, and `UseCase` suffixes. Input types use `Command` for mutations and `Query` for composite queries; results use semantic names such as `AssessmentSummary`, `PublishedAssessment`, or `LoginResult`. For HTTP, use `Request`/`Response`. Do not require `Dto` in internal contracts.

Operations may receive zero parameters, a simple value, or a cohesive object. Use objects for composite inputs; do not wrap a command in another object merely to satisfy a signature. `HealthProbeDto`, `TransactionRunDto`, and the `LoginStudentDto(LoginCommand)` wrapper must be removed when they are only intermediaries.

Use narrow ports per capability, not one interface per database method or a universal store per feature. Separate read and write ports when consumers and responsibilities differ. Do not create interfaces for every internal class.

Separate authentication, session resolution, and logout into `LoginInputPort`, `ResolveSessionInputPort`, and `LogoutInputPort`, with corresponding use cases. These flows serve the roles defined by the contract; their names must not artificially restrict them to students. Share only cohesive mechanisms that are actually needed.

### D-04 — Typed, immutable internal contracts

Incoming and outgoing ports, commands, queries, results, and projections must declare the data they carry. Use `val` and read-only collections; copy mutable buffers when shared state could otherwise be modified.

`val` and Kotlin read-only collections do not guarantee deep immutability. Prevent mutable aliases from escaping at contract boundaries, including mutable elements and arrays, with immutable values or defensive copies where needed. Add a focused aliasing test for affected contracts that accept mutable data; do not add an immutable-collections dependency by default.

Typing must also protect validity. Model operation-specific states so that required data cannot be absent in a successful/published state and failure cannot masquerade as absence. Put invariant checks in cohesive constructors, factories, or transition functions, including rehydration where appropriate. Do not expose setters, factories, or generated copy paths that bypass those checks. Use existing contract invariants rather than inventing stricter business rules; avoid a generic state-machine framework.

Prohibit `Any`/`Object`, `JsonNode`, raw types, star projections, and `Map<String, Any?>` as shortcuts in known business contracts. A class containing `body: Any?` is not a typed contract. Maps with semantic key and value types, such as answers by question, remain valid.

Use `Page<T>` for internal pagination where the contract is the same, with a concrete type at usage sites. Adapt it to the existing HTTP format without changing public fields. Keep projections separate from entities when they represent different data; do not create redundant conversions between identical objects.

Use enums for actual closed vocabularies, with explicit conversion preserving their HTTP/SQL values. Identifiers retain their current representation; introduce value classes only for identifier confusion demonstrated by actual calls. Do not change rounding, precision, or public numeric types during extraction; keep `BigDecimal` where the rule already requires decimal precision.

Single-item queries may return `T?` when `null` means only absence. Operations with distinct states, such as revision conflict and absence, use a specific sealed result or explicit exceptions already compatible with the contract; never an ambiguous `Boolean`/`null`. Do not create a universal `Result<T>` or error hierarchy for all features.

The only technical exceptions to concrete contracts must be narrow and identified by signature, such as the transaction port's `<T> run(block: () -> T): T`. Do not exempt entire packages. Heterogeneous receipt/event data must receive versioned models and infrastructure codecs that preserve historical bytes and records, rather than a generic JSON tree in the domain.

### D-05 — Transport translates; application coordinates; domain decides

Jackson, Bean Validation, HTTP status codes, cookies, headers, and knowledge of the JSON format belong in incoming/outgoing adapters. Use cases receive typed internal data without interpreting HTTP field names.

Preserve the contracted order of authentication, authorization, and validation. Do not turn all validation into deserialization or early `@Valid` processing: if validation must happen after authorization, keep a translation/validation step in the adapter in the correct order. Parsing and validation errors receive the normative status and body, not automatic framework defaults.

For PATCH, use the fixed internal representation **`FieldChange<T>` with `Unchanged` and `Set(value: T)`**. A nullable field uses `FieldChange<T?>`; a non-nullable field uses `FieldChange<T>`. Absence becomes `Unchanged`; explicit `null` may become `Set(null)` only when the contract permits it. Keep this type small and framework-free in `shared/application/contracts`, without a generic JSON payload. The domain receives the desired state or explicit facts, not the PATCH mechanism.

Detect unknown fields and invalid types in the adapter according to the contract. Do not silently discard data or convert invalid input into a default.

Every input port must enforce the operation's authorization and business preconditions when called without HTTP. Adapters establish trusted identity and translate syntax; application/domain code validates the actor's permission for the operation and protects its invariants. Never trust caller-supplied roles or ownership merely because the input is typed. Preserve the existing actor/session revalidation protocol. Transport checks do not substitute for business checks, and internal callers do not receive an implicit authorization bypass. Test direct input-port calls for rejected unauthorized actions and invalid state transitions as well as successful operations. Preserve HTTP error precedence without moving business enforcement exclusively into controllers or filters.

### D-06 — Policies are pure and specific

Policies receive facts and return decisions. They do not query ports, execute callbacks, perform I/O, start transactions, serialize JSON, generate UUIDs/tokens, or obtain the current time. `Instant`, numeric values, and domain objects are legitimate inputs. Use `java.time.Clock` injected into the application; do not create an unnecessary `ClockPort`.

Preserve cohesive policies such as score calculation and editing restrictions. Split generic policies by actual decision, not by line count. A simple function may remain alongside the type containing the rule; there is no need to create a class for every condition.

Place invariants intrinsic to an entity/value with that concept and expose valid transitions. Use a policy for a cohesive decision that does not naturally belong to one entity/value, not as the mandatory destination for every rule. Both behavior-bearing objects and immutable data with cohesive pure transition functions are valid. In either style, callers must not reconstruct rules or freely manufacture invalid states. Do not produce data-only models plus a collection of unrelated policy procedures.

Remove parsing, remote fetching, replay, and hashing from `AssessmentPolicy`. Remove idempotency reads/writes and command execution from `MaterialsPolicy` and `ClassroomPolicy`. In `SubmissionPolicy` and `CurriculumPolicy`, separate authorization/state decisions from parsing and response construction. The application coordinates these steps; adapters implement external mechanisms.

Do not replace `Support` with `Policy` while keeping the same contents. Use cases must make relevant steps and side effects visible instead of hiding the entire operation in a helper.

### D-07 — Atomic writes preserve the existing protocol

The application owns the operation, required atomicity, and business workflow; the domain owns decisions and state transitions; adapters own transaction, locking, and persistence mechanisms. Two patterns are authorized:

1. **An atomic operation behind a typed write port:** the application requests a named business operation with explicit preconditions, outcomes, and all-or-nothing effects. The adapter maintains the indivisible locked-read/revalidation/write protocol. It may invoke an operation-specific pure domain decision/transition on the locked facts. Selection and ordering of business rules must be defined in that domain operation, not reconstructed in JDBC branches. The adapter applies its result and translates persistence conflicts into the port's contracted outcomes. It must not own HTTP parsing, business workflow, remote calls, or cross-feature orchestration.
2. **An application-coordinated transaction:** use `TransactionRunnerOutputPort.run(block)` when the application needs to coordinate multiple ports or the operation cannot remain a cohesive atomic port without embedding business workflow in the adapter. Implement it with `TransactionTemplate` and the existing transaction manager. The callback belongs to the application. The port does not receive a DTO wrapping the function. Keep state reads, domain decisions, and writes inside the same required transaction; do not extract decisions into an unsafe pre-transaction check.

Do not force every write into the second pattern or fragment an atomic port into generic CRUD. Do not perform authorization only before the transaction. Preserve the existing lock order, session/actor/relationship/revision revalidation, propagation, isolation, and rollback. Calling domain behavior inside an adapter is allowed specifically to decide based on protected facts, not to relocate the use case into persistence. Replacing JDBC must not require rediscovering or reimplementing the business rule sequence. For every affected atomic port, document its behavioral contract and test domain decisions without a database, the application with a substitute, and the actual atomic protocol against PostgreSQL.

Remote snapshot fetches happen before the final transaction. Revalidate the required facts before commit. External messaging must not happen while a business transaction remains open. Business data and outbox records are written in the same transaction; preserve the existing publication, confirms, retry, and redrive protocol.

Do not change SQL, schemas, or migrations in this refactoring. Extraction changes types and code placement while preserving queries and effects. A finding requiring SQL changes beyond this equivalence is a scope blocker, not permission to rewrite persistence.

### D-08 — Idempotency, serialization, and shared have explicit boundaries

`shared` contains only technical capabilities actually in use: transaction execution, technical idempotency contracts, pagination/partial updates, common HTTP filters, and error infrastructure. It does not contain business rules, academic roles, assessment state, or session models.

Place `OriginFilter` in `shared/adapters/in/web/filters` and common HTTP handlers/mapping in `shared/adapters/in/web/errors`. Health retains its entry point, use case, probe port, and adapter, without an empty DTO. Business exceptions remain with their owner.

Do not retain `JsonSerializerOutputPort.serialize(Any?)` as a general shared API merely to make ArchUnit pass. Where the application needs a fingerprint or codec for idempotency, expose a typed capability for the operation; the Jackson mechanism stays in the adapter. Reuse internal serialization/hash implementations where equivalent, without building a custom serialization framework.

Before changing representations, characterize fingerprints, field order, absent/null behavior, numbers, and receipts. Preserve keys, hashes, TTL, permanent receipts, and compatibility with existing records. Do not reserialize old responses incompatibly. Tests must compare before-and-after data/bytes, not merely equality between new maps.

### D-09 — Errors and readability

HTTP handlers translate errors; the domain/application does not carry HTTP status codes. Preserve normative categories and precedence. Catch specific exceptions when they can be distinguished; do not convert unexpected defects into input errors, absence, or success. Preserve useful causes without exposing sensitive data.

Remove unsafe casts, `!!`, and suppressions when a typed representation addresses the cause. Unavoidable interoperability exceptions must be localized, justified, and tested. Do not address unrelated deprecation warnings merely to expand the diff.

Use one primary declaration per file with a matching name. Allow closely related sealed subtypes, small results, extensions, and private helpers in the same file; do not widen visibility merely to split them. Prohibit generic files that collect unrelated responsibilities. Use syntax analysis for structure, not regex.

### D-10 — Architecture verified through three complementary mechanisms

1. **ArchUnit**, already adopted: dependencies, contract exposure, signatures, and cycles.
2. **Kotlin PSI**, through `kotlin-compiler-embeddable` in tests only and aligned with the compiler version: bounded source structure and syntax checks that bytecode cannot resolve. PSI alone does not provide complete semantic type resolution. Do not implement a custom parser or add a separate analysis platform.
3. **Semantic review:** type meaning, cohesion, side-effect ordering, responsibility, and behavioral equivalence. Do not claim that ArchUnit proves complete purity or business correctness.

Import production classes through the classpath/root package, excluding tests, without hardcoding `build/classes/kotlin/main` as the only source. Share the import across rules. Test class presence and non-empty selection in required groups, without requiring artificial packages for features that do not need them.

Rules must protect D-01–D-09, including configuration, published contracts, calls between adapters, cycles, prohibition of ports/I/O in policies, and inappropriate types at boundaries. Traverse properties and generic types of project contracts while tracking visited nodes; do not recursively scan libraries indiscriminately. Resolve generic types at the usage site; `Page<T>` and the transaction exception are not violations in themselves.

Record each rule's enforcement mechanism and limits in the rule matrix: bytecode dependencies/signatures, PSI syntax, compiler-checked typed fixtures, behavioral tests, or semantic review. Use PSI for declaration/file structure, not claims about resolved aliases, inferred types, or data-flow nullability. Verify nullability and state validity with compiler-checked typed fixtures and executable contract tests; do not infer them solely from `rawParameterTypes`. Generic substitutions must be supported by the actual analysis mechanism and fixtures; record unsupported cases and cover their guarantees with targeted tests/review instead of silently declaring them compliant. Distinguish generated Kotlin code, such as `equals(Any?)`, from the business API. Do not build a custom Kotlin type checker.

Each relevant rule has positive and negative fixtures proving the reason for failure. Keep fixtures outside the production import. Fix the controller test that was inside a domain-only filter. Consolidate the old text-based tests; do not retain fragile regex checks alongside them as if they provided additional protection.

Prove isolation behaviorally: execute representative affected input ports without Spring, HTTP, or a database, with deterministic substitutes for external ports. For critical persistence/atomic ports, run a shared behavioral contract suite against an in-memory implementation and the real adapter for supported observable semantics: absence, typed conflicts, authorization facts, transitions, and replay where applicable. Substitutes must reuse domain decisions rather than duplicate them or return unconditional success. Keep concurrency, isolation, SQL constraints, rollback, and lock tests against PostgreSQL; an in-memory substitute is not evidence of those guarantees. Record which contract assertions are shared and which require real infrastructure. Do not create a second production persistence implementation or a generic fake framework.

### D-11 — Gradual introduction without hiding violations

Create a dedicated diagnostic task, `architectureAudit`, that runs the target rules against production, generates a report, and fails when violations exist. Initially, do not attach target rules that are not yet compatible to the standard `test` task. In GP-02, explicitly replace old rules superseded by D-01/D-03/D-09, including mandatory DTOs and the old port location, with checks of the new decisions and fixtures. Keep compatible guarantees mandatory; update their selectors in the same issue as each move, covering old and new packages while they coexist, without losing coverage or treating an empty selection as a pass. Rule and fixture tests belong in `test` from the start.

This allows the instrumentation issue to be completed with passing fixtures and an expected failing production report. Acceptance of that issue requires a complete, traceable inventory of violations; it neither requires nor asserts service compliance. This is the only special treatment of intermediate gates authorized here.

Promote rule sets into mandatory `test` execution as their corresponding fixes are verified. A promoted rule must not become diagnostic-only again to accommodate regressions. Do not use freezing/baselines, broad exclusions, global `allowEmptyShould`, or violation snapshots to mask results.

At the final gate, all rules approved in the matrix belong in `test`/`check`, and `architectureAudit` also passes. Until then, report suite status, architecture status, and semantic issues separately.

### D-12 — Runtime OpenAPI documentation is part of the deliverable

Complete R-08 with springdoc MVC UI at the exact version officially compatible with the build you find. The patch version must be chosen by verifying availability/compatibility during execution, not by copying an unchecked historical range.

Use an opt-in `docs` profile, OpenAPI 3.1, the public group `/v3/api-docs/core-public`, and UI `/swagger-ui.html`. Outside the profile, JSON/YAML/UI are inaccessible. Within the profile, disable the ungrouped document and expose only the required public Core group.

Check all enabled endpoint variants, schemas, paths, and UI configuration for private/internal/operational/outbox leaks. Compare the public Core subset with the [normative contract](../http/openapi.json), without overwriting it or inventing operations. Preserve security, status codes, schemas, nullability, and the `aja_session` cookie; do not add an authentication bypass or attempt to set an HttpOnly cookie manually.

## 3. Planning and automatic execution workflow

### 3.1 Preflight and baseline

Record HEAD, branch, staged/unstaged/untracked Git state, tools, and dependencies. Consult Graphify before navigation when present. Reuse a suitable checkout and a dedicated branch; use `codex/` for a new branch. Do not create an unnecessary worktree or lose local changes when switching branches.

Classify existing changes as relevant, unrelated, or not yet understood. Integrate relevant changes only after assigning them to issues and reviewing them. Do not reset/stash/discard indiscriminately, commit unrelated content, or present pre-existing work as new.

Revalidate the actual baseline. Historical evidence exists for R-00–R-06 and 12 passing focused tests, but neither proves the current checkout. The latest review found `confirmResetDrafts: null` being accepted as absence: reproduce this at the method level and over HTTP before fixing it.

Check the JDK, Gradle, Docker, PostgreSQL, RabbitMQ, and required services. The existence of a README or scripts does not prove availability. Preserve the actual commands; do not use frontend scripts as backend evidence.

### 3.2 Create the issues and maintain traceability

Update [core-refactoring-plan.md](core-refactoring-plan.md) and create the complementary backlog in `docs/backend/core-golden-path-issues.md`, with evidence in dedicated files when needed. Preserve historical records; distinguish completion at the time from current compliance. Update [ARCHITECTURE.md](../../ARCHITECTURE.md) with the organization decided for Core and the future reference, without claiming that the other services have been migrated.

Each issue needs an identifier, objective, origin in R-07/R-08/R-09/a finding, dependencies, files/consumers/operations, verifiable criteria, commands, services, invariants, risks, executor, reviewers, and status per criterion. Create a resumable execution record with a concrete next action. Issues are local, not remote publications.

Use the following order as the required structure; subdivide migrations by flow to keep diffs reviewable:

| Stage | Deliverable | Dependency |
| --- | --- | --- |
| GP-00 | Baseline, issue plan, decision synchronization, and ownership/export/data-dependency matrices with transaction coordinators; focused ECC review only | None |
| GP-01 | Confirmed contract regressions, starting with absent/null behavior for `confirmResetDrafts` | GP-00 |
| GP-02 | Rule/mechanism/limits matrix, fixtures, bounded PSI checks, isolation/port-contract test inventory, and architectural/semantic violation report | GP-00; required compilation must work |
| GP-03 | R-07 technical foundations and identity/academic migration to the decided boundaries | GP-01, GP-02 |
| GP-04.x | Typing, policies, and application per flow: curriculum; materials; assessment/authoring; attempts; submissions; announcements; forum; attendance; dashboard | GP-03 and actual dependencies between flows |
| GP-05 | Completed R-07, health/Origin/error verification, and revalidation of the R-06 outbox | Affected GP-04.x issues |
| GP-06 | R-08: springdoc, restricted profile, and compatibility/exposure tests | GP-05 and stabilized production contracts |
| GP-07 | Final R-09: final gate evidence, golden path guide, and documentation closure; focused ECC review only | All preceding stages |

Bringing R-09 instrumentation forward does not close R-09 before R-08. Do not repeat R-00–R-06 in full without evidence of drift; revalidate the affected guarantees. Do not use old evidence to waive tests for code changed now.

After recording the plan and completing GP-00's focused ECC consistency-and-links review, automatically start the first eligible issue. Do not stop to ask whether to execute it.

### 3.3 Executor, reviewers, and execution efficiency

Follow the [agent workflow](../../AGENTS.md), with the explicit GP-00/GP-07 exception below: `gpt-6-sol` coordination at medium reasoning when available in the host; one `gpt-6-luna` executor at medium reasoning per issue for code, tests, validation, and fixes; an independent `gpt-6.1-sol` ECC reviewer at medium reasoning using [code-reviewer](../agents/code-reviewer.md); and a separate `gpt-6.1-sol` reviewer at medium reasoning using [ponytail-review](../../.agents/skills/ponytail-review/SKILL.md). Use the `database-reviewer` agent when persistence, transactions, concurrency, or privileges are affected. Reviewers verify implementation and concrete risks; architectural defects in the baseline follow the evidence-based correction process in section 1, not preference-driven redesign.

**GP-00 and GP-07 use only ECC review.** These documentation/planning stages require one independent `gpt-6.1-sol` ECC reviewer at medium reasoning, focused on consistency with the authoritative contracts and the correctness of links/references. Do not launch Ponytail or database reviews for either stage, including when their documentation describes persistence or transactions. This user-authorized exception supersedes the repository's default docs-only review requirements for these two stages. Do not turn GP-07 into a new full-code review: reference the completed implementation reviews and validation evidence. Required final execution gates still apply. If closure reveals a code/test/configuration fix, assign it to an implementation issue with its applicable reviews before resuming documentation closure; keep GP-00 and GP-07 documentation/planning-only.

Do not pretend to change the coordinator's model through a text instruction: host settings have limits. Record actual unavailability of a required role. Reuse the same reviewers for fixes. Do not create concurrent executors for the same issue, and make it clear that the checkout is shared.

Implement one issue at a time. Reviews start only after the diff is stable and evidence is available. Follow [multi-agent efficiency](../../.agents/skills/multiagent-token-economy/SKILL.md): batched inspections, sufficient context, concise messages, and focused checks between checkpoints. Do not repeat broad suites without a change or a new hypothesis.

### 3.4 Per-issue criteria and real validation

Perform a preflight before editing each issue. For behavior/regressions: a focused test, executed and recorded RED, a minimal fix, GREEN on the same test, and refactoring while green. For extractions: characterization and equivalence of the entire flow. Follow [TDD](../../.agents/skills/tdd-workflow/SKILL.md) and [verification](../../.agents/skills/verification-loop/SKILL.md), subordinate to the actual Kotlin contracts.

Validate policies without frameworks; the application with port substitutes; adapters with real PostgreSQL/RabbitMQ where relevant; affected HTTP operations and errors; session/authorization before validation; and rollback, locks, concurrency, revisions, replay, snapshots, and outbox where affected.

Each migrated flow must provide direct input-port authorization/invariant tests, relevant invalid-state/aliasing tests, and the D-10 shared port-contract checks for its critical adapters. Record the business decision's owner and show that adapter-specific branches implement technical mechanics rather than a hidden use case. Track SQL read/write dependencies alongside code dependencies and verify the declared cross-feature transaction coordinator.

For `confirmResetDrafts`, cover absence, null, false, true, invalid types, and authorization precedence. For PATCH typing, also cover permitted removal and required fields. When replacing dynamic JSON, cover unknown fields, numbers/limits, arrays, ordering, and hash/receipt equivalence.

Use the actual Core suite/build at stable checkpoints, with the explicit GP-02/D-11 treatment for rules that are still diagnostic. For documentation, verify links, claims, and commands; do not run services unnecessarily. Record commands, exit codes, tests, and limitations; do not claim execution based on inspection.

Reviewers inspect the complete issue diff and necessary context. Reproduce confirmed findings, add relevant regression tests, fix them, and request rechecks of the affected criteria. Record dismissed findings and their justification. An unresolved blocker prevents issue closure.

### 3.5 Blockers, commits, and continuity

If an essential tool, service, access, or information is missing, record the evidence and suspend the dependent issue. Continue an eligible independent issue if one exists. If none can progress, explain the required intervention. Do not implement speculatively when mandatory acceptance criteria cannot be verified.

Create cohesive local commits after gates and reviews, using Conventional Commits and an issue footer; include only assigned and reviewed changes. Do not rewrite history or include secrets or Graphify artifacts. After code changes, update the graph and keep hooks enabled.

Do not interpret elapsed time as approval, request permission again for already authorized work, or silently expand scope to resolve an external dependency.

## 4. Final golden path acceptance

The objective is complete only when:

- [ ] All decisions D-01–D-12, including any recorded evidence-based corrections, have implementation and evidence; contract deviations are not hidden.
- [ ] R-07, R-08, and the Core criteria of R-09 are proven in the final checkout.
- [ ] `test`/`check` include all approved rules; `architectureAudit` finds no violations.
- [ ] Fixtures demonstrate valid rules and actual violations; semantic review remains separate from automated approval.
- [ ] Inputs/outputs and projections are typed; artificial wrappers and generic JSON trees do not replace contracts.
- [ ] Policies are pure and cohesive; the application coordinates; atomic protocols preserve guarantees.
- [ ] Business invariants and authorization hold for direct input-port calls; valid transitions cannot be bypassed through exposed model construction/mutation paths.
- [ ] Domain behavior is independent of JDBC; critical port contracts have shared substitute/real-adapter evidence and separate real-infrastructure concurrency/rollback evidence.
- [ ] Feature ownership, SQL data dependencies, cross-owner write protocols, and transaction coordinators are explicit; features are not mislabeled as proven bounded contexts.
- [ ] Immutability claims account for aliases/elements, and rule coverage distinguishes syntax, resolved types, behavior, and semantic review.
- [ ] Actual Core compilation, the full suite, and build pass; affected operations have no regressions.
- [ ] Implementation issues have their applicable ECC, Ponytail, and database reviews complete, with no blockers; GP-00 and GP-07 have only the focused ECC contract-consistency and links review specified in section 3.3.
- [ ] Runtime OpenAPI respects the public Core contract and exposes no private information.
- [ ] The plan, execution record, evidence, and guide describe the existing code.

Run `scripts/acceptance-b16.ps1` only when its prerequisites and the backlog state make it applicable. Derive counts from the contract/ownership; do not confuse Core coverage with coverage of all three services. If unavailable or inapplicable, record that condition precisely and keep integrated acceptance pending, without implementing external backlog work to force its execution.

Deliver `docs/backend/core-golden-path-guide.md` with real examples of a query, a transactional write, a remote call followed by persistence, a policy, typed input/results, errors, tests, and documentation. Explain where to place new code, when a simple function is sufficient, entity/value invariants versus policies, and application workflow versus atomic adapter mechanics. Include a direct input-port test and a shared port-contract test from the migrated code. Separate architectural principles, AJA conventions, and migration constraints; describe code and data dependencies and any evidence-based decision corrections. Do not create a separate idealized example.

In the final report, separate suite quality, structural compliance, typing, semantic review, Core functional validation, and integrated acceptance. List issues, commits, commands, and limitations. A green suite alone does not mean the architecture is ready; an unexecuted integrated acceptance does not mean the entire backend is validated.

## 5. Technical implementation references

The decisions above are specific to AJA, not universal framework requirements. These references support the selected mechanisms:

- [Alistair Cockburn: Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/): inside/outside boundaries, purposeful ports, and isolated application execution; it does not mandate this package tree.
- [Robert C. Martin: The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html): inward dependencies, use-case coordination, and domain behavior expressed as objects or data/functions.
- [Service Layer](https://martinfowler.com/eaaCatalog/serviceLayer.html): application operations and transaction coordination.
- [Martin Fowler: Bounded Context](https://martinfowler.com/bliki/BoundedContext.html): model/language boundaries, which are not established by package names alone.
- [Martin Fowler: Anemic Domain Model](https://martinfowler.com/bliki/AnemicDomainModel.html): the risks of removing behavior from an object-oriented domain model; not a prohibition on cohesive functional domain modeling.
- [Kotlin collections overview](https://kotlinlang.org/docs/collections-overview.html): read-only interfaces versus immutable data.
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html): dependencies, cycles, importing, and test integration.
- [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html): files and semantically related declarations.
- [Spring programmatic transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html): `TransactionTemplate` for imperative flows; keep it behind the application port.
- [Springdoc compatibility](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot-): confirm the exact version when executing R-08.

These sources do not supersede local contracts or authorize changes to the other services' architecture.
