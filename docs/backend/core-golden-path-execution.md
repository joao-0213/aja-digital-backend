# Core golden path execution index

This file is an index, not a second evidence log. The [local issue backlog](core-golden-path-issues.md) owns current statuses, dependencies, and acceptance criteria. Each issue's linked evidence file owns its preflight, exact commands/results, review findings, and disposition. Read only the active issue and evidence unless a dependency requires more context.

| Stage | Status | Canonical record |
| --- | --- | --- |
| GP-00 | Complete | [Local issue backlog](core-golden-path-issues.md#local-issue-order-and-status) |
| GP-01 | Complete | [GP-01 evidence](core-golden-path-gp01-evidence.md) |
| GP-02 | Instrumentation complete, Core rules enforced in GP-07.3 | [GP-02 evidence](core-golden-path-gp02-evidence.md) |
| GP-03 | Complete | [GP-03 evidence](core-golden-path-gp03-evidence.md) |
| GP-04.1 | Complete | [GP-04.1 evidence](core-golden-path-gp041-evidence.md) |
| GP-04.2 | Complete | [GP-04.2 evidence](core-golden-path-gp042-evidence.md) |
| GP-04.3 | Complete | [GP-04.3 evidence](core-golden-path-gp043-evidence.md) |
| GP-04.4 | Complete | [GP-04.4 evidence](core-golden-path-gp044-evidence.md) |
| GP-04.5 | Complete | [GP-04.5 evidence](core-golden-path-gp045-evidence.md) |
| GP-04.6 | Complete | [GP-04.6 evidence](core-golden-path-gp046-evidence.md) |
| GP-04.7 | Complete | [GP-04.7 evidence](core-golden-path-gp047-evidence.md) |
| GP-04.8 | Complete | [GP-04.8 evidence](core-golden-path-gp048-evidence.md) |
| GP-04.9 | Complete | [GP-04.9 evidence](core-golden-path-gp049-evidence.md) |
| GP-05 | Complete | [GP-05 evidence](core-golden-path-gp05-evidence.md) |
| GP-06 | Complete | [GP-06 evidence](core-golden-path-gp06-evidence.md) |
| GP-07.1 | Complete | [GP-07.1 evidence](core-golden-path-gp071-evidence.md) |
| GP-07.2 | Complete | [GP-07.2 evidence](core-golden-path-gp072-evidence.md) |
| GP-07.3 | Complete | [GP-07.3 evidence](core-golden-path-gp073-evidence.md) |
| GP-07 | Complete for Core | [GP-07 evidence](core-golden-path-gp07-evidence.md) |

## Working-tree guardrails

- The original baseline and issue order are recorded in the local issue backlog. The current tree may contain unrelated work; inspect `git status` and the relevant diff before assigning changes to an issue.
- Historical evidence describes the revision it tested. Revalidate only checks affected by the active change; do not repeat broad checks for a known unavailable dependency.
- The local Graphify output is ignored and may be stale. Query it before source navigation when present; refresh it after code changes. Never commit graph artifacts.
