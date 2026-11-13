# B-16 verification record

## Scope and baseline

- Starting revision: `7ac5088d7a4f4762df010d1c905323a14215fb83` (B-15).
- Preserved pre-existing local changes: `AGENTS.md` was modified and `.codex/` was untracked before this issue. Neither belongs to B-16 or is included in its diff.
- B-16 acceptance: validate OpenAPI structure and all 98 path/method operations; exercise each operation over real HTTP with positive and pertinent denial cases; trace service-role/internal-call behavior; verify restart, outbox, dependency failure and snapshot behavior; report commands, outcomes and untested limits.
- Entrypoints: 95 Core operations and 3 Questions operations from `docs/http/openapi.json`; Email has no public OpenAPI operation and is covered as the Core outbox consumer/worker.
- No service schema, migration, public contract, production code, gateway or frontend change was made.

## Graphify

Queried the existing graph before source inspection. Queries located B-16, `docs/http/openapi.json`, operation ownership, Core and Questions application tests, the Core HTTP controllers, the Email worker tests, and the relevant withdrawal operations. Follow-up queries located `OriginFilter`/health handling, logout, Questions session checks and role/validation tests. After changes, `graphify . --update` was attempted but could not extract 17 changed documents without an LLM API key. `graphify . --update --code-only` completed and re-extracted 136 code files (25 AST cache misses); it skipped the 17 documents, including the updated verification record. Later code-only updates re-extracted 26 and then 25 changed code files; the final graph has 2,206 nodes and 6,300 edges. The final query, `graphify query "B16 live HTTP coverage and deterministic attendance fixtures" --budget 500`, located the Core coverage filter/helper and attendance store/use-case nodes. Graphify reported 23 SQL files unindexed because `tree_sitter_sql` is not installed, plus parser warnings for three files. Graphify describes planned contracts; runtime claims below come from process-backed test evidence.

## Operation coverage mechanism

`scripts/acceptance-b16.ps1` validates OpenAPI 3.1.0 version 1.0.8, counts path/method operations, rejects missing/duplicate operation IDs or empty responses, and resolves internal references. It observed exactly 98 operations and 998 resolved references.

The Core and Questions test suites load that same OpenAPI document. A test-only servlet filter matches requests by exact HTTP method and Spring `PathPattern`, associates them with the operation, and records status codes. Existing service tests provide the successful requests; the end-of-suite assertion requires a 2xx for every operation when the aggregate runner sets `B16_OPENAPI_COVERAGE=true`. This keeps ordinary targeted `--tests` runs independent while the acceptance runner remains strict. A separate probe generated for every operation checks its appropriate denial response and verifies that the status is declared by that operation:

- Core session-protected routes receive a no-session request and must return their declared 401. Path and query inputs come from OpenAPI parameter examples or schema fallbacks, including resolved parameter references. Body-bearing operations use their OpenAPI JSON examples; required idempotency headers are supplied. Multipart upload uses a contract-shaped multipart request.
- Public auth operations receive `{}` and must return declared 422 validation errors. `/health` with a disallowed Origin must return declared 403.
- `/auth/logout` accepts a missing cookie by contract and is asserted as 204, not treated as a denial.
- Questions routes receive no session, use the OpenAPI path ID/example and the practice request example where applicable, and must return declared 401.

The probe test also records each actual response against the generated method/path pattern. This captures early responses such as the health Origin filter and multipart route that can short-circuit before the coverage filter. Core assertions cover 95 positive plus expected-status pairs; Questions assertions cover 3 positive plus 401 pairs.

## Validation results

Final aggregate command:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\acceptance-b16.ps1
```

Result: **PASS**. It reported 98/98 operations, unique operation IDs, 998 resolved references; then ran these service gates with `--rerun-tasks`:

| Command | Result |
| --- | --- |
| `services/core/gradlew.bat test build --rerun-tasks --no-daemon` (`B16_OPENAPI_COVERAGE=true`) | PASS, 122 tests, 0 failures, 6m16s |
| `services/questions/gradlew.bat test build --rerun-tasks --no-daemon` (`B16_OPENAPI_COVERAGE=true`) | PASS, 26 tests, 0 failures, 1m19s |
| `services/email/gradlew.bat test build --rerun-tasks --no-daemon` (`B16_OPENAPI_COVERAGE=true`) | PASS, 16 tests, 0 failures, 1m33s |

The runner then executed real-process acceptance scripts:

- `scripts/acceptance-b03-email-process-restart.ps1`: PASS. Core-first unroutable outbox delivery survived Email binding; confirmation/reset tokens were consumed through Core; provider retry returned 429 then 202; Email was killed/restarted; crash state moved `sending` to `unknown` with one provider request.
- `scripts/acceptance-b08-assessment-snapshots.ps1`: PASS. Source edit/withdrawal retained snapshot and image bytes; metadata update worked with Questions offline; adding a new question returned 503 and preserved the draft.

Docker daemon and configured JDK 21 were available. The process acceptances used isolated Docker Compose PostgreSQL/RabbitMQ services. The application suites use real Testcontainers PostgreSQL and the configured RabbitMQ test service.

## Failed checks and corrections

- The first Core coverage assertion passed the 2xx sweep but failed because its generic 4xx expectation had no per-operation probes. Replaced that with OpenAPI-derived, operation-specific requests and declared expected statuses.
- Initial unauthenticated `GET /attendance/eligible` returned 400 because the probe omitted query values. The probe now supplies contract examples/schema values; the exact request returns the expected 401.
- The first mutation sweep sent empty bodies and returned 422 before authentication. The probe now sends OpenAPI request examples and idempotency headers, allowing the missing-session check to reach 401.
- A full Core run then failed only its aggregate status collector: the health 403 and multipart 401 were returned before the servlet filter recorded them, and logout correctly returned 204. The probe now records its direct HTTP status against the exact generated operation, and logout expects 204 in both the probe and aggregate. Final Core suite passed.
- The first Questions run sent a Core-allowed Origin that Questions rejects, producing 403 before session checking. Questions probes now omit Origin (matching its contract tests); the final Questions suite passed.
- An earlier aggregate attempt saw the B-08 process fail to become healthy within its 120-second startup window. Standalone `scripts/acceptance-b08-assessment-snapshots.ps1` then passed, and the final isolated invocation from the aggregate runner passed. This transient first failure is retained here rather than represented as a passing first run.
- Independent reviews found that unconditional operation aggregates made method-filtered Gradle runs fail for unrelated routes. The Core and Questions `@AfterAll` checks now run only when `B16_OPENAPI_COVERAGE=true`; the B-16 runner sets it around all service test/build tasks, uses `--no-daemon` so the one-shot Gradle process inherits that environment, and restores the prior process environment in `finally`. Focused Core role and Questions session tests both pass with the flag unset; the final aggregate above passes with it enabled.
- Review also caught the original `/users?role=teacher&active=true&q=Admin%20AJA` empty-result assertion being removed during the live conversion. It is restored with its original MockMvc semantics, while the live HTTP teacher listing and role-change checks remain. A trial live HTTP version of the q assertion returned one item, so it was not used to replace that existing assertion. The Core focused role test passes after restoring the original query check.
- The first strict `--no-daemon` aggregate exposed date-dependent attendance fixtures: four attendance-focused tests used the current wall clock as `enrolledAt` while asserting against fixed `2026-11-12` sessions. On `2026-11-13`, valid current/exited enrollments were after those sessions, so live create returned 422 and the exited student was reported `beforeEnrollment`. The four fixtures now use explicit `2026-11-12T00:00:00Z` enrollment instants where appropriate (the exited row ends at 02:00Z); the four-method focused attendance run passed in 1m28s, and the final strict aggregate passed after this correction.

## Evidence limits

- The per-operation collector proves one real 2xx and one operation-appropriate denial/idempotent response by method/path/status. It does not validate every response schema, every input field, or every response header for all 98 operations.
- It is not a Cartesian operation-by-role matrix. Role and authorization decisions are covered by the existing domain integration tests (for example, Core scoped dashboard/session tests, Core protected-read/session tests, Questions role restrictions, and practice validation precedence), plus the live Core↔Questions process scenarios. These tests cover representative role and ordering cases rather than every role on every path.
- Email has no public operation in the aggregate OpenAPI; worker delivery/retry/restart behavior is covered by its full suite and B-03 real-process acceptance.
- No database schema limits or migrations changed in B-16; database behavior is validated through the existing service tests and isolated real PostgreSQL process scenarios, not by a new exhaustive cross-schema privilege matrix.
- Browser/gateway behavior and frontend simulator suites are outside this issue's acceptance.
