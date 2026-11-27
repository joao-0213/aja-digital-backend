# GP-04.9 evidence — Dashboard read composition

## Preflight

- Starting revision `48897a3bca40e05c57af1c3637c04ca95187865a` on `codex/core-golden-path`. The Git index and Core service tree were clean. Existing documentation and repository changes are outside this issue.
- `graphify query 'GetDashboardUseCase DashboardController DashboardConfiguration DashboardIntegrationTests GP-04.9 bounded projection academic announcement assessment forum' --budget 950` located the HTTP entry point, composition, Spring wiring, and live projection tests.
- Scope is `GET /me/dashboard`. Core composes summaries through the feature owners. Student projections contain progress, resume state and the student’s own pending counts. Staff projections use the actor’s authorized classes, students, enrollment requests and feedback counts. All navigated resources retain their own authorization.
- Java 21 and the Gradle wrapper are available. PostgreSQL and RabbitMQ run through Testcontainers, and the existing Questions boot JAR is available for the full Core suite.
- Baseline `./gradlew test --tests '*DashboardIntegrationTests' --console=plain` in `services/core` passed eight tests in 50 seconds, with zero failures, errors or skips. It covers role scope, revocation, full counts with bounded rows, topics, announcements and the Brasília date boundary.

## Acceptance

| Criterion | Status |
| --- | --- |
| Typed, immutable student and staff Dashboard projections | Verified by focused and full tests |
| Uses only explicitly published, bounded incoming read capabilities | Verified by source inspection and live tests |
| Source tables, columns, authorization and limits documented | Verified by database review |
| Direct composition, role, and source-failure tests and live PostgreSQL tests | Verified by focused and full tests |
| Full Core test and build, Graphify and independent reviews | Verified on the final candidate |

## Source queries and authorization

Each row describes the owner of a Dashboard read. `.*` records a wildcard select in the existing query. Current-session checks use the actor’s `users.{id,active,role}` and `sessions.{user_id,token_hash,revoked_at,expires_at}` in owners that revalidate the session. Dashboard resolves the session through Identity before composing the reads.

| Owner and reads | Sources | Scope and bounds |
| --- | --- | --- |
| Academic classes and counts | `classes.*`, `enrollments.{class_id,status,student_id,normalized_email,enrolled_at,legacy}`, `subjects.{id,class_id}`, `subject_teachers.{subject_id,teacher_id}`, `users.{id,role,active,normalized_email}`. Staff totals reuse the existing `enrollments.*` and `users.*` page queries and read only their totals. | Up to 100 active classes and their active enrollment counts. Teacher results use current teacher assignments. The staff totals retain the one-row page and existing distinct student count and pending enrollment filters. The student’s pending count uses their ID or normalized email. |
| Curriculum subject summaries | `subjects.*`, `subject_teachers.{subject_id,teacher_id}`, `users.{id,name,role,active,email_confirmed_at}`, `classes.{id,name,active}`, `enrollments.{class_id,status,student_id}`, `subject_publications.{subject_id,publication_id}`, `subject_visits.{subject_id,user_id,publication_ids}`. | First 100 by ID. Teacher assignment or the student’s active enrollment controls visibility. Class state controls `canManageContent`, and the actor’s visit controls `hasUpdates`. |
| Assessment summaries and counts | `assessments.*`, `modules.{id,subject_id,withdrawn_at}`, `subjects.{id,class_id}`, `classes.{id,active}`, `subject_teachers.{subject_id,teacher_id}`, `users.{id,role,active,email_confirmed_at}`, `enrollments.{class_id,status,student_id,unassigned_assessment_ids}`, `assessment_questions.{assessment_id,question_id,source,source_revision,content_hash,snapshot,position}`, `attempts.{assessment_id,user_id,submitted_at}`, `submissions.{assessment_id,user_id,submitted_at,feedback_published}`. | First 100 published assessments. Students see eligible assignments in active classes and modules, and their pending count excludes submitted attempts and work. Staff feedback counts submitted work without published feedback and scopes teachers to their assigned subjects. The advisory lock for this count remains part of the read protocol. |
| Materials progress, totals and resume | `materials.{id,title,module_id,kind}`, `material_progress.*`, `modules.{id,subject_id,withdrawn_at}`, `subjects.{id,class_id}`, `enrollments.{class_id,status,student_id}`, `users.{id,active,email_confirmed_at}`. | Student-only read of their progress on visible materials in non-withdrawn modules. Counts include all visible materials. Progress is limited to 100 and resume returns the latest incomplete item. |
| Announcements and upcoming events | `announcements.*`, `users.{id,name,active,role,email_confirmed_at}`, `subject_teachers.{subject_id,teacher_id}`, `subjects.{id,class_id}`, `classes.{id,active}`, `enrollments.{class_id,status,student_id,unassigned_assessment_ids}`, `assessments.{id,title,description,kind,deadline,status,module_id}`, `modules.{id,subject_id,withdrawn_at}`. | Five visible notices and five upcoming events. Each upcoming source limits its rows before the merge. The range is today through 30 days ahead in Brasília. Course visibility, assignment, withdrawal and publication filters remain in the owner. |
| Forum topic summaries | `topics.{id,title,subject_id,class_id,withdrawn_at,last_activity_at}`, `topic_replies.{topic_id,withdrawn_at}`, `subjects.{id,name}`, `subject_teachers.{subject_id,teacher_id}`, `classes.{id,active}`, `enrollments.{class_id,student_id,status}`, `users.{id,active,role,email_confirmed_at}`. | Five visible topics by latest activity with only summary fields and the count of non-withdrawn replies. Teacher assignment and active confirmed student enrollment remain scoped in Forum. |

## Candidate validation

- The baseline `./gradlew architectureAudit --console=plain` exited 1. It identified the generic response exposed by `GetDashboardInputPort`. The baseline Dashboard composition also passed internal Academic queries and read totals from generic Academic pages.
- Dashboard now accepts the authenticated actor and returns typed student or staff projections. The new Academic read capability composes the existing queries for class summaries and totals. The class mapper builds its typed result directly from the original SQL and bindings.
- `./gradlew test --tests '*DashboardIntegrationTests' --tests '*GetDashboardAcademicUseCaseTests' --console=plain` passed 11 tests on the final candidate. The live tests cover role-specific shapes and bounded queries. The direct tests cover read order, limits, student-only counts, immutability and source failure propagation.
- The final `./gradlew architectureAudit --console=plain` reports 53 Core findings and no Dashboard findings. The original Dashboard findings and the generic class summary port finding are absent from the report.
- Final `./gradlew test build --rerun-tasks --console=plain` in `services/core` passed in 6m45s. JUnit XML records 234 tests across 46 suites with zero failures, errors or skips. `graphify update .` completed on the final candidate.
- The scoped whitespace check passed. `git diff --exit-code HEAD -- docs/http/openapi.json docs/http/contract.md services/core/src/main/resources/db/migration` passed.

## Independent reviews

- The ECC reviewer approved without findings after reviewing field shapes and omissions, role-specific counts, immutable collections, source order, authorization and the error mapping.
- The registered PostgreSQL reviewer approved without findings after checking the SQL, bindings, limits, role scopes, full counts and advisory lock. The reviewer confirmed all 65 SQL literals in `JdbcAcademicStore` are in the original order and verified the source inventory against the queries.
