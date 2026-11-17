# Core golden path execution record

## GP-00 baseline

**GP-00 status: complete.** Focused independent ECC review and recheck: **APPROVE**, zero outstanding findings.

| Item | Current evidence |
| --- | --- |
| Date / revision / branch | 2026-11-17; `d10642107e86464631584d7c2f4424eac40a910b`; `codex/backend-b01-b16`. |
| Git state | 870 changed/untracked paths reported by the coordinator; 475 untracked; 846 under `services/core/src/main`, 17 under Core tests; no staged changes. The worktree includes broad pre-existing changes. Do not attribute them to GP-00 or commit them here. |
| Relevant pre-existing changes | `ARCHITECTURE.md`, `docs/backend/core-refactoring-plan.md`, and broad Core source/test/build work appear related to golden path/R-09 planning or implementation. The assigned document diffs were inspected; the full Core diff has not been reviewed, so those source/build changes remain not-understood for acceptance and must be audited issue-by-issue. |
| Unrelated pre-existing changes | `.gitignore` and `AGENTS.md` are outside GP-00's authorized scope and were not changed by this issue. Other top-level/config/skills changes have not been inspected enough to classify individually; treat as not-understood, preserve them. |
| Graphify | `graphify-out/graph.json` exists. Queries were run before source navigation; returned R-00–R-06 nodes/declarations that predate much of the current worktree. Mark graph stale; never use it as current dependency proof. Refresh is required only after a code issue changes sources. |
| Current toolchain | System `java`, Gradle without explicit JAVA_HOME, and Docker commands are absent from PATH. Explicit `C:\Users\joaov\.jdks\azul-21.0.13.0.3\bin\java.exe` is available; with `JAVA_HOME=C:\Users\joaov\.jdks\azul-21.0.13.0.3`, `services/core/gradlew.bat --version` succeeds: Gradle 9.8.2, Kotlin 2.4.10, Windows 11. Compile/unit tasks are available. Docker/Podman/Nerdctl and PostgreSQL/RabbitMQ CLI/process availability have not been established; do not claim Testcontainers infrastructure is usable. |
| Historical evidence | R-00–R-06 documents record historical clean/verified states, including tests/builds with Docker/Testcontainers at their own revisions. Those results do not validate the current worktree or replace current affected checks. |
| Contracts read | Orchestration prompt, `AGENTS.md`, `ARQUITETURA.md`, `ARCHITECTURE.md`, operation ownership, normative OpenAPI, outbox operations, core plan and R-00–R-06 evidence. |

### Change classification

- **Relevant but not accepted as verified:** architectural/plan docs and broad Core source/test/build modifications that align by path/topic with the golden path. Full source diff, callers, and behavior have not been reviewed; GP-01/02 must establish ownership and evidence before relying on them.
- **Unrelated to GP-00:** existing edits to `.gitignore` and `AGENTS.md`; preserved untouched.
- **Not understood:** uninspected changed/untracked configuration, skills, docs, and source details. Preserve them and classify under their authorized issue only after targeted inspection.

These categories describe topic and review state, not authorship. No baseline file was reverted, staged, or committed.

## Acceptance checklist

| Criterion | Status | Evidence |
| --- | --- | --- |
| Starting revision, branch, staged/unstaged/untracked state recorded | verified | Table above and coordinator `git status` evidence. |
| Graphify query precedes source navigation; freshness limitation explicit | verified | Query executed; graph returned prior R-00–R-06 declarations and is stale against current changes. |
| Existing changes classified without destructive cleanup | verified | Classification above; no reset/stash/discard. |
| D-01–D-12 authority and distinctions synchronized | verified | [Local issues](core-golden-path-issues.md), [ARCHITECTURE.md](../../ARCHITECTURE.md), and plan header. |
| GP-00–GP-07 and GP-04 flow subissues ordered with dependencies and per-criterion statuses | verified | [Local issue backlog](core-golden-path-issues.md). |
| Ownership, exports, actual allowed dependency edges, data dependencies, and transaction coordinators documented | verified | The backlog records a query-backed cross-owner baseline with source columns where explicit, all persisted columns where SQL uses `*`, current data owner/consumer, authorization scope, and affected tests. It is not an exhaustive inventory of every Core query; each flow must revalidate its complete queries and row mappings before edits. |
| Documentation links and claims checked; no code/service checks claimed | verified | Relative Markdown targets and local heading anchors pass for all four updated documents; `git diff --check` passes; mojibake/replacement scan is clean. No service/runtime check is claimed. |
| Current dirty-tree forced compilation | verified | Explicit JDK `JAVA_HOME`; `compileKotlin compileTestKotlin --rerun-tasks --quiet --console=plain` exit 0. Compilation only, no tests/runtime/infra proof. |
| GP-01 implementation readiness | blocked | Required HTTP/PostgreSQL services are unavailable/unproven. Only read-only diagnosis and service-readiness checks may occur; no GP-01 production or test implementation is permitted until availability is rechecked. GP-02 remains eligible. |
| Focused independent ECC contract-consistency/links review | verified | Final recheck: APPROVE, zero outstanding findings. The two initial findings and their fixes are recorded below. GP-00 uses ECC review only; no Ponytail or database review applies. |

### ECC findings and recheck

| Finding | Resolution | Recheck |
| --- | --- | --- |
| Align `ARCHITECTURE.md` file organization with D-09's one-primary-declaration convention and related subtype/helper allowance. | Updated the convention and PSI verification guidance; no mandatory split for every private helper or subtype remains. | Approved; no outstanding finding. |
| Hold GP-01 implementation because required HTTP/PostgreSQL acceptance services are unavailable, and make GP-02 next eligible. | Backlog now blocks GP-01 production/test changes and limits interim work to read-only diagnosis/readiness. Execution next action is GP-02 preflight after GP-00 closure. | Approved; no outstanding finding. |

## Commands and validation

| Command/check | Outcome |
| --- | --- |
| `git rev-parse HEAD`; `git branch --show-current`; staged/unstaged/untracked status | Captured as above; no staged changes, broad dirty tree. |
| `graphify query "GP-00 core golden path D-01 D-12 R00 R06"` | Completed; 539 nodes, truncated output; returned historical R00–R06 references, confirming graph staleness. |
| `services/core/gradlew.bat --version` with explicit JDK `JAVA_HOME` | Exit 0 per coordinator; Gradle 9.8.2 / Kotlin 2.4.10. No Gradle tests/build run for this documentation-only issue. |
| `$env:JAVA_HOME='C:\Users\joaov\.jdks\azul-21.0.13.0.3'; .\gradlew.bat compileKotlin compileTestKotlin --rerun-tasks --quiet --console=plain` from `services/core` | Exit 0 on the current dirty worktree per coordinator. This proves forced main and test-source compilation only; it does not prove tests, runtime behavior, database/broker availability, or architecture compliance. |
| Docker / PostgreSQL / RabbitMQ availability | Docker and other container CLIs not found; service availability not proven. No claim of live infrastructure. |
| Documentation checks | Relative Markdown target/heading-anchor check passed for `ARCHITECTURE.md`, `core-refactoring-plan.md`, `core-golden-path-issues.md`, and this record; `git diff --check` passed; mojibake/replacement-character scan returned no matches. |

## Next action

Run **GP-02 preflight**: verify current compilation/toolchain readiness, identify D-10 rule mechanisms/fixtures and acceptance criteria, then proceed with its diagnostic architecture instrumentation. GP-01 remains held: only read-only diagnosis/readiness checks are allowed until required HTTP/PostgreSQL services are established. GP-03 remains blocked on GP-01 and GP-02 completion.

