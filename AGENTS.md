# Repository Guidelines

## Scope and architecture

This repository contains the planned Kotlin/Spring Boot backend for AJA Digital. The 16 issues in `ISSUES-BACKEND.md` are open; do not infer that documentation, mock/frontend evidence, or the service README means an issue is implemented. Read `ARQUITETURA.md`, the relevant issue, `docs/backend/operation-ownership.md`, and applicable contract documents before changing a service.

Keep Core, Questions, and Email as separate hexagonal services with separate PostgreSQL schemas/roles. No service reads or migrates another service's schema. Core owns identity and academic authorization. Questions calls Core only through authenticated private interfaces. Core uses a transactional outbox and RabbitMQ to Email; Email owns only its worker state. Do not introduce a public gateway, frontend integration, BFF, or cross-schema coupling in B-01–B-16. Preserve the OpenAPI contract, including `aja_session` cookie behavior, status codes, identifiers, pagination, schemas, and request/response shapes. Generic Java/Spring/JWT/Kafka/Redis examples in skills never override these contracts or the chosen Kotlin architecture.

## Backlog coordination and review workflow

Use `gpt-6-luna` with medium reasoning for routine coordination. Assign each issue implementation, tests, API/worker validation, and review fixes to one `gpt-6-luna` executor with high reasoning. Use an independent `gpt-6.1-sol` reviewer at medium reasoning for ECC review, and a different independent `gpt-6.1-sol` reviewer at medium reasoning with the `ponytail-review` skill. Keep each reviewer assigned for rechecks after fixes. Escalate ambiguous architecture, authorization, or concurrency decisions to Sol; reserve xhigh for a concrete difficult problem and record why. User instructions take precedence. Skipping reviews for this repository preparation does not remove the default workflow for future issues.

Apply to every issue, including docs-only issues:

Start with `git status`, relevant history and diffs. Preserve pre-existing edits and staged files; never include unrelated changes. A request for one issue authorizes only that issue. When the whole backlog is explicitly requested, create or reuse a dedicated local branch (`codex/` prefix for a new task branch), respect dependencies, and continue with the next eligible issue. Repository preparation alone does not authorize implementation. Model/effort settings of the coordinator are selected through host controls, not changed by this file.

1. Record starting Git revision, acceptance criteria, affected operations/entry points, and Graphify query results. Use graph query before source searches/reads whenever `graphify-out/graph.json` exists; read only located source context.
2. For behavior changes, use ECC TDD: add focused regression/contract test, run and record valid RED with command/test/failure, implement minimal fix, rerun same test for GREEN, then refactor green. Do not claim a RED based only on an unrun test.
3. At stable checkpoints, run the backend's actual test suite, type/static/architecture checks, and build as defined by B-01. B-01 must choose real tools first; do not assume Kotlin frameworks, Gradle tasks, coverage thresholds, Docker commands, or frontend `npm` scripts. Test service boundaries with real PostgreSQL/RabbitMQ or equivalent real processes; record exact commands and results. Exercise changed API operations against real processes and relevant failures. Docs-only changes verify claims, links and commands.
4. Obtain a read-only ECC review from an independent agent using `docs/agents/code-reviewer.md`, then a separate read-only Ponytail review using `.agents/skills/ponytail-review/SKILL.md`. Both inspect the complete issue diff and acceptance evidence. Reproduce confirmed findings, add regression tests where applicable, rerun affected checks, then have the same reviewers recheck. Record dismissed findings and any blocker.
5. Close only verified criteria. Stage only issue files, create a local Conventional Commit per cohesive issue with issue footer, and report hash, actual validation/review outcomes, and remaining changes. Never push, rewrite history, commit secrets, or include local graph artifacts.

## Skills and Graphify

Use the installed ECC TDD and verification guides at `.agents/skills/tdd-workflow/SKILL.md` and `.agents/skills/verification-loop/SKILL.md`; use `.agents/skills/ponytail/SKILL.md` and `.agents/skills/ponytail-review/SKILL.md` for Ponytail review. Domain skills available for approved backend work are `.agents/skills/{kotlin-patterns,kotlin-testing,springboot-patterns,springboot-security,hexagonal-architecture,jpa-patterns,postgres-patterns,database-migrations,docker-patterns}/SKILL.md`. These provide guidance, not authorization to execute backlog issues or decide B-01 tools. Check contract and architecture before applying generic advice.

Graphify output `graphify-out/` is local and ignored. If its graph exists, run `graphify query` before source search/read; use `graphify path`/`explain` for focused relations. After code changes run `graphify update .`. Keep local hooks enabled; generated graph artifacts are never committed. If graph output is absent, use normal source navigation.

See `docs/agents/skills.md` for skill provenance, approved additions and precedence, and `docs/graphify.md` for local installation and document-index commands. The current graph describes planned contracts, not running services. No skill example authorizes changing authentication to JWT, disabling CSRF, cross-schema SQL, importing Supabase-specific authorization, adding infrastructure, or performing destructive resets. Choose test/coverage/migration tools only in the relevant implementation issue.

## References and security

`docs/http/` is a behavior contract copied from frontend reference commit `2257d2af4950983f067f7f20b4d2e6b021f365e8`; examples of JSON Server, `npm` commands, local files, and simulator storage are not backend implementation requirements. See `docs/reference/frontend-sources.md`. Use fictitious data only. Never commit secrets, tokens, personal data, or runtime state.
