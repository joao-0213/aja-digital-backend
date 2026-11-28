# Repository Guidelines

## Scope and Architecture
- **Contracts:** Strictly preserve `ARCHITECTURE.md`, `docs/backend/operation-ownership.md`, and OpenAPI specs (`aja_session` cookies, status codes, IDs, pagination, payloads). Generic skill examples never override these contracts (no JWT, CSRF disabling, Kafka, Redis, or Supabase auth).

## Multi-Agent Roles & Workflow
Only for backlog execution, TDD, checkpoints, and commit rules, read `docs/agents/backlog-workflow.md` first. Otherwise, follow user instructions.

- **Coordinator:** `gpt-6.1-terra` (low reasoning). Escalate ambiguous architecture, auth, or concurrency decisions to Sol; user instructions take precedence.
- **Executor:** One `gpt-6-luna` (medium reasoning) per issue for implementation, tests, validation, and fixes.
- **ECC Reviewer:** Independent `gpt-6.1-terra` (medium reasoning, using `docs/agents/code-reviewer.md`) after a complete diff. Reuse the same reviewer for targeted rechecks.
- **Database Reviewer:** Fixed to `gpt-6.1-terra` (medium reasoning, `.codex/database-reviewer.toml`) for any SQL, schema, migration, query, transaction, or concurrency change. Cannot be overridden.
- **Git hygiene:** Start with `git status`/diff, preserve pre-existing edits, work one issue per commit (Conventional Commits with issue footer), and never push, rewrite history, or commit secrets, real data, or `graphify-out/`.

## Skills & Graphify
- **Graphify:** If `graphify-out/graph.json` exists, run `graphify query` before searching/reading source files, and `graphify update .` after code changes. See `docs/graphify.md`.