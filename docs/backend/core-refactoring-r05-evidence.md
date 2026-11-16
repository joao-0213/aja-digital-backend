# Core refactoring R-05 — Extract deterministic policies

**Starting revision:** `e13d75b` (`codex/backend-b01-b16`)

## Summary of changes
Extracted deterministic assessment decision logic from persistence adapters and use cases into pure Kotlin policy objects under `assessment/domain/policies/` (zero Spring, JDBC, Jackson, Jakarta, or I/O imports):
1. `AssessmentEditPolicy`: `isValidKindQuestionCount`, `canPublish`, and `resolvePostSubmissionDeadline` (`America/Sao_Paulo` civil-date preservation and structural-edit lock rules), replacing inline checks in `JdbcAssessmentStore` and `ManageAssessments`.
2. `AttemptScoringPolicy`: `areAnswersValid` and `calculateScore` (BigDecimal `HALF_UP` scale 2), replacing inline scoring/answer validation in `JdbcAttemptStore`.
3. `DeadlinePolicy`: `isBeforeDeadline` (exclusive deadline / `allowLate` check), replacing duplicate `beforeDeadline` helpers in `JdbcAttemptStore` and `JdbcSubmissionStore`.
4. `SubmissionPolicy`: `isValidFeedbackGrade`, `canPublishFeedback`, and `areStagedFilesValid`, replacing inline checks in `JdbcSubmissionStore`.

SQL queries, SQL authorization, pagination, advisory/row locks, transaction boundaries, idempotency, and HTTP behavior remain unchanged.

## Review fixes applied
- `JdbcAttemptStore` (`start`) and `JdbcSubmissionStore` (`create`) now call `DeadlinePolicy.isBeforeDeadline(...)`; deleted unused private `beforeDeadline`, `brasilia`, `ZoneId`, and `RoundingMode` imports; removed unused `ZoneId` import from `JdbcAssessmentStore`.
- `JdbcAssessmentStore.publish()` restores the `status != "draft"` `AcademicConflict` short-circuit before `hasSubmissions(id)` / `questionSignature(id)` queries.
- `AttemptScoringPolicy.calculateScore()` adds `require(questions.isNotEmpty())`.
- `AssessmentEditPolicyTests` timezone test crosses UTC midnight (`2026-11-11T01:00:00Z` -> `2026-11-16T01:00:00Z`); `AttemptScoringPolicyTests` adds `.xx5` `HALF_UP` tie (`2.53`), `0`-correct (`0.00`), and empty-questions guard tests.

## Verification
- Policy unit tests (`13` `@Test` methods across `AssessmentEditPolicyTests` `5`, `AttemptScoringPolicyTests` `3`, `DeadlinePolicyTests` `2`, `SubmissionPolicyTests` `3`) + `ArchitectureTests` (`5` tests): exit `0`.
- `.\gradlew.bat test --rerun-tasks --quiet --console=plain` (`B16_OPENAPI_COVERAGE` unset): exit `0` (`22` suites, `152` tests, `0` failures, `0` errors, `0` skipped).
- `$env:B16_OPENAPI_COVERAGE = 'true'; .\gradlew.bat build --rerun-tasks --quiet --console=plain` (cleared after): exit `0` (`22` suites, `152` tests, `0` failures, `0` errors, `0` skipped; `CoreOpenApiCoverageFilter` verified all `95` non-`/questions` operations in `docs/http/openapi.json`).

## Review record
- Independent Database review (`r05-db` recheck): `APPROVE`.
- Independent Ponytail review (`r05-ponytail` recheck): `APPROVE` (`Verdict: Ship.`).
- Independent ECC review (`r05-ecc` recheck): `APPROVE` (`0` CRITICAL/HIGH/MEDIUM).
