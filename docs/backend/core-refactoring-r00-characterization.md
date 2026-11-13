# Core refactoring R-00 — current-state characterization

**Issue:** R-00 (phase 0). **Status:** acceptance criteria verified; ECC and Ponytail reviews completed, findings applied and rechecked (ECC's final recheck was blocked by a provider quota limit and verified locally). Review record at the end of this file.
**Plan:** [core-refactoring-plan.md](core-refactoring-plan.md). **Target architecture:** [ARCHITECTURE.md](../../ARCHITECTURE.md).

This document is read-only evidence for future R-issues. It does not authorize behavior changes.

## Starting point

| Item | Value |
| --- | --- |
| Branch | `codex/backend-b01-b16` |
| Commit | `fa633b0614935aca596a4503971c69f724541c1a` |
| Working tree | clean at start (`git status --porcelain` empty) |
| Service | `services/core` (Kotlin 2.3.21, Spring Boot 4.1.2, Gradle 9.8.2, JDK 21) |
| JDK used | Azul 21.0.13 (`%USERPROFILE%\.jdks\azul-21.0.13.0.3`), set via `JAVA_HOME` because `java` is not on `PATH` |
| Docker | 29.9.3, daemon reachable (Testcontainers PostgreSQL/RabbitMQ available) |
| Graphify | `graphify-out/graph.json` present (2251 nodes); queried before source reads |
| Secrets | `CORE_DATABASE_PASSWORD`, `CORE_OUTBOX_ENCRYPTION_KEY`, `CORE_OUTBOX_OPERATOR_TOKEN`, `CORE_QUESTIONS_SERVICE_TOKEN`, `RABBITMQ_PASSWORD` are required from the environment at runtime; no values recorded here |

## Baseline commands and outcomes

Run from `services/core` with `JAVA_HOME` pointing at the Azul 21 JDK.

| # | Command | Exit | Outcome |
| --- | --- | --- | --- |
| 1 | `.\gradlew.bat test --console=plain` | 0 | `BUILD SUCCESSFUL` but every task was `UP-TO-DATE`; **no test executed**, so this is not valid baseline evidence |
| 2 | `.\gradlew.bat test --rerun-tasks --console=plain` | 0 | `BUILD SUCCESSFUL in 8m 49s`, `8 actionable tasks: 8 executed`; full recompilation and execution of `CoreApplicationTests`, `ArchitectureTests`, `HealthControllerTests`, `OriginFilterTests`, `SessionCookieTests`, `HttpQuestionSnapshotProviderTests` against real PostgreSQL/RabbitMQ containers. Log: `r00-test-baseline.log` (local, not committed). |
| 3 | `.\gradlew.bat build --console=plain` | 0 | `BUILD SUCCESSFUL in 6s`, `8 actionable tasks: 8 up-to-date`; not valid on its own because nothing was rebuilt |
| 4 | `.\gradlew.bat build --rerun-tasks --console=plain` | 0 | `BUILD SUCCESSFUL in 6m 38s`, `8 actionable tasks: 8 executed`; compiles main and test, assembles `bootJar`/`jar`, then reruns the full test suite. Log: `r00-build-baseline.log` (local, not committed). |

### Baseline result

`test --rerun-tasks` (authoritative test evidence for R-00):

```
> Task :test
BUILD SUCCESSFUL in 8m 49s
8 actionable tasks: 8 executed
```

`build --rerun-tasks` (authoritative aggregate evidence for R-00):

```
> Task :compileKotlin
> Task :bootJar
> Task :jar
> Task :assemble
> Task :compileTestKotlin
> Task :test
> Task :check
> Task :build
BUILD SUCCESSFUL in 6m 38s
8 actionable tasks: 8 executed
```

Only pre-existing Kotlin deprecation/unchecked-cast warnings were emitted; there are no test failures and no compile errors. PowerShell 5.1 renders JVM stderr as `NativeCommandError` blocks in the log; both process exit codes were `0` and Gradle reported `BUILD SUCCESSFUL`.

## Operation-to-controller map

Public API routes are under `/api/v1`, private routes under `/internal`, and Actuator exposes `/actuator/health`. Authenticated browser routes resolve `aja_session` through `AuthenticateStudent.resolve` and pass an `AcademicActor` down. Routes that do not resolve a session: the session entry points `AuthController.register`/`login`/`logout` (they create or destroy it), the four body-credential token routes `requestEmailConfirmation`/`confirmEmail`/`requestPasswordReset`/`resetPassword`, and `HealthController`, `OriginFilter`, and Actuator.

### identity

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| POST `/api/v1/auth/register` | `AuthController.register` | `RegisterStudent.register` | `IdentityStore.createRegistration` |
| POST `/api/v1/auth/login` | `AuthController.login` | `AuthenticateStudent.login` | `IdentityStore.findLoginIdentity`, `rotateSession`, `recordLoginFailure` |
| GET `/api/v1/auth/session` | `AuthController.session` | `AuthenticateStudent.resolve` | `IdentityStore.findSession` |
| POST `/api/v1/auth/logout` | `AuthController.logout` | `AuthenticateStudent.logout` | `IdentityStore.revokeSession` |
| POST `/api/v1/auth/email-confirmation-requests` | `AuthController.requestEmailConfirmation` | `RequestAuthToken.request` | `IdentityStore.recordAudit`, `issueAuthToken` |
| POST `/api/v1/auth/email-confirmations` | `AuthController.confirmEmail` | `RequestAuthToken.confirmEmail` | `IdentityStore.confirmEmail` |
| POST `/api/v1/auth/password-reset-requests` | `AuthController.requestPasswordReset` | `RequestAuthToken.request` | `IdentityStore.recordAudit`, `issueAuthToken` |
| POST `/api/v1/auth/password-resets` | `AuthController.resetPassword` | `ResetPassword.reset` | `IdentityStore.resetPassword` |
| GET `/api/v1/me` | `SelfController.me` | `AuthenticateStudent.resolve` | `IdentityStore.findSession` |
| PATCH `/api/v1/me` | `ProfileController.update` | `UpdateOwnProfile.update` | `IdentityStore.updateProfile` |
| GET `/internal/v1/session` | `InternalSessionController.inspect` | `AuthenticateStudent.resolve` | `IdentityStore.findSession` |
| POST `/internal/v1/session/practice` | `InternalSessionController.practice` | `ManageAttempts.practiceBlocked` | `AttemptStore.practiceBlocked` |
| POST `/internal/v1/question-files/{id}` | `ContentController.provisionQuestionImage` (guarded by `FileUploadSessionFilter`) | `ManageContent.provisionQuestionImage` | `ContentStore.provisionQuestionImage` |

### classroom

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| GET/POST `/api/v1/users`, GET/PATCH `/api/v1/users/{id}` | `AcademicController.users/createUser/user/updateUser` | `ManageAcademics` | `AcademicStore` |
| POST `/api/v1/users/{id}/role`, `/deactivate`, `/withdraw` | `AcademicController.role/deactivate/withdraw` | `ManageAcademics` | `AcademicStore` |
| GET/POST `/api/v1/classes`, GET/PATCH `/api/v1/classes/{id}` | `AcademicController.classes/createClass/classById/updateClass` | `ManageAcademics` | `AcademicStore` |
| POST `/api/v1/classes/{id}/close`, `/reopen` | `AcademicController.closeClass/reopenClass` | `ManageAcademics` | `AcademicStore` |
| GET/POST `/api/v1/enrollments`, GET `/api/v1/enrollments/preview`, GET `/api/v1/enrollments/{id}` | `AcademicController.enrollments/createEnrollment/preview/enrollment` | `ManageAcademics` | `AcademicStore` |
| POST `/api/v1/enrollments/{id}/activate`, `/cancel`, `/end` | `AcademicController.activateEnrollment/cancelEnrollment/endEnrollment` | `ManageAcademics.transitionEnrollment` | `AcademicStore` |
| GET `/api/v1/me/enrollments` | `AcademicController.myEnrollments` | `ManageAcademics.myEnrollments` | `AcademicStore` |
| GET `/api/v1/students`, GET `/api/v1/students/{id}` | `AcademicController.students/student` | `ManageAcademics` | `AcademicStore` |

### curriculum

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| GET/POST `/api/v1/subjects`, GET/PATCH `/api/v1/subjects/{id}` | `AcademicController.subjects/createSubject/subject/updateSubject` | `ManageSubjects` | `SubjectStore` |
| GET/POST `/api/v1/subjects/{id}/visits` | `AcademicController.getVisit/recordVisit` | `ManageSubjects.visit` | `SubjectStore.visit` |
| GET/POST `/api/v1/modules`, GET/PATCH `/api/v1/modules/{id}` | `AcademicController.modules/createModule/module/updateModule` | `ManageSubjects` | `SubjectStore` |
| POST `/api/v1/subjects/{id}/reorder-modules` | `AcademicController.reorderModules` | `ManageSubjects.reorder` | `SubjectStore.reorderModules` |

### materials

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| POST `/api/v1/files` (multipart) | `ContentController.upload` (guarded by `FileUploadSessionFilter`) | `ManageContent.upload` | `ContentStore.upload` |
| GET `/api/v1/files/{id}` | `ContentController.file` | `ManageContent.file` | `ContentStore.file` |
| GET `/api/v1/files/{id}/content` | `ContentController.download` | `ManageContent.download` | `ContentStore.download` |
| POST `/api/v1/files/{id}/discard` | `ContentController.discard` | `ManageContent.discard` | `ContentStore.discard` |
| GET `/api/v1/materials`, GET `/api/v1/materials/{id}` | `ContentController.materials/material` | `ManageContent` | `ContentStore` |
| POST `/api/v1/materials/batch` | `ContentController.publish` | `ManageContent.publish` | `ContentStore.authorizeMaterialPublication`, `publishMaterials` |
| PATCH `/api/v1/materials/{id}` | `ContentController.update` | `ManageContent.update` | `ContentStore.updateMaterial` |
| POST `/api/v1/modules/{id}/reorder-materials` | `ContentController.reorder` | `ManageContent.reorder` | `ContentStore.reorderMaterials` |
| GET/PATCH `/api/v1/me/progress` | `ContentController.progress/recordProgress` | `ManageContent` | `ContentStore` |

### assessment

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| GET/POST `/api/v1/assessments`, GET/PATCH `/api/v1/assessments/{id}` | `AssessmentController.assessments/create/assessment/update` | `ManageAssessments` | `AssessmentStore`, `QuestionSnapshotProvider` |
| POST `/api/v1/assessments/{id}/publish` | `AssessmentController.publish` | `ManageAssessments.publish` | `AssessmentStore.publish` |
| GET/POST `/api/v1/attempts`, GET `/api/v1/attempts/{id}` | `AttemptController.attempts/start/attempt` | `ManageAttempts` | `AttemptStore` |
| PATCH `/api/v1/attempts/{id}/answers`, POST `/api/v1/attempts/{id}/submit` | `AttemptController.saveAnswers/submit` | `ManageAttempts` | `AttemptStore` |
| GET `/api/v1/attempts/{id}/result` | `AttemptController.result` | `ManageAttempts.result` | `AttemptStore.result` |
| GET/POST `/api/v1/submissions`, GET/PATCH `/api/v1/submissions/{id}` | `SubmissionController.submissions/create/submission/update` | `ManageSubmissions` | `SubmissionStore` |
| POST `/api/v1/submissions/{id}/submit`, PATCH `/api/v1/submissions/{id}/feedback`, POST `/api/v1/submissions/{id}/publish-feedback` | `SubmissionController.submit/saveFeedback/publishFeedback` | `ManageSubmissions` | `SubmissionStore` |

### announcements

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| GET/POST `/api/v1/announcements`, GET/PATCH `/api/v1/announcements/{id}` | `AnnouncementController.list/create/get/update` | `ManageAnnouncements` | `AnnouncementStore` |
| POST `/api/v1/announcements/{id}/withdraw` | `AnnouncementController.withdraw` | `ManageAnnouncements.withdraw` | `AnnouncementStore.withdraw` |
| GET `/api/v1/calendar` | `AnnouncementController.calendar` | `ManageAnnouncements.calendar` | `AnnouncementStore.calendar` |

### forum

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| GET/POST `/api/v1/topics`, GET `/api/v1/topics/{id}` | `AcademicController.topics/createTopic/topic` | `ManageTopics` | `TopicStore`, `QuestionSnapshotProvider` (question targets only) |
| POST `/api/v1/topics/{id}/replies`, `/read`, `/solution`, `/withdraw` | `AcademicController.reply/readTopic/solution/withdrawTopic` | `ManageTopics` | `TopicStore` |
| POST `/api/v1/topics/{id}/replies/{replyId}/vote`, `/withdraw` | `AcademicController.voteReply/withdrawReply` | `ManageTopics` | `TopicStore` |

### attendance

| Operation | Controller method | Use case | Store port |
| --- | --- | --- | --- |
| GET `/api/v1/attendance`, `/eligible`, `/{id}` | `AcademicController.attendanceList/eligibleAttendance/attendanceById` | `ManageAttendance` | `AttendanceStore` |
| POST/PATCH `/api/v1/attendance` | `AcademicController.createAttendance/updateAttendance` | `ManageAttendance` | `AttendanceStore` |

### dashboard, health, outbox

| Operation | Controller method | Use case / adapter |
| --- | --- | --- |
| GET `/api/v1/me/dashboard` | `DashboardController.dashboard` | `ManageDashboard` (calls `ManageAcademics`, `ManageSubjects`, `ManageContent`, `ManageAssessments`, `ManageSubmissions`, `ManageAnnouncements`, `ManageTopics`) |
| GET `/api/v1/health` | `HealthController.health` | `CheckServiceHealth` → `HealthProbe` |
| GET `/actuator/health` | Spring Boot Actuator | `management.endpoints.web.exposure.include=health` |
| POST `/internal/ops/outbox/{id}/redrive` | `OutboxOperationsController.redrive` | `OutboxRedrive.redrive` |
| (scheduled) | `OutboxPublisher.publishAvailable` | direct JDBC + `RabbitTemplate` |

## Transaction, lock, revision, and idempotency invariants per write

Rules that R-01–R-09 must preserve:

1. **Transaction owner.** Most controller methods are not transactional. Exceptions: 27 `AcademicController` write methods carry `@Transactional` (but `createTopic` does not), the `AnnouncementController` write methods carry it, and in `ContentController` only `publish` does. Identity controllers are not transactional; their boundary is the `@Transactional` store method. Assessment, attempt, submission, topic, attendance, and announcement writes also enter a `@Transactional` store method, so a controller-level annotation joins that boundary instead of replacing it. Remote calls made by use cases (`HttpQuestionSnapshotProvider.snapshots`) happen before the store method opens its transaction.
2. **Serialization.** `pg_advisory_xact_lock(812734)` is taken by most write store methods, for example: identity `recordLoginFailure`, `rotateSession`, `revokeSession`, `confirmEmail`, `resetPassword`, `updateProfile`; academic `isCurrentAdminSession` (only when `serializeAdminInvariant` is set), `updateUser`, `changeRole`, `deactivate`/`withdraw`; content `upload`, `discard`, `authorizeMaterialPublication`, `publishMaterials`, `updateMaterial`, `reorderMaterials`, `recordProgress`; subject/module `authorize`, `createSubject`, `updateSubject`, `createModule`, `updateModule`, `reorderModules`, `visit`; assessment `authorizeWrite`, `create`, `update`, `publish`; `JdbcAttemptStore` and `JdbcSubmissionStore` write methods; topic `authorizeAndFindReplay`, `authorizeReply`, `create`, `reply`, `read`, `solution`, `withdraw`, `vote`, `withdrawReply`; announcement `authorizeCreate`, `create`, `update`, `withdraw`; attendance `create` and `update`.
   Treat that list as representative, not exhaustive: some paths reach the lock through a parameter (`JdbcAttendanceStore.requireWriter(…, lock = true)`), so the per-store `lockAuthInvariant()` call sites are authoritative.
   It is **not** taken by `createRegistration`, `recordAudit`, `issueAuthToken`, `createUser`, `createClass`/`updateClass`/`setClassActive`, `createEnrollment`/`transitionEnrollment`, or `provisionQuestionImage`; those rely on unique constraints, `FOR UPDATE` row locks, or the revision compare-and-set. Idempotency uses a separate `pg_advisory_xact_lock(hashtextextended("<actor>|<operation>|<target>|<key>",0))` inside `JdbcIdempotencyStore.find`.
3. **Lock order.** Orders observed in the reviewed SQL: session/user (`users` → `sessions`); email confirmation (`auth_tokens` read → `users` → `classes` ordered by id → `enrollments` ordered by class_id,id → `auth_tokens`); enrollment create (`users` by normalized email → `classes`); enrollment transition (`classes` → `enrollments`); assessment write (`classes` → `users` for teacher → `subjects` → `modules` → `assessments`); submission submit (`submissions` → `files` ordered by id); feedback (`classes` → `users` for teacher → `submissions`); content publish (`materials` ordered by order_index,id → `files`); attendance create/update (`classes` → `users` for actor → `subjects` → `attendance`); announcement write (`classes` → `subjects` → `announcements`). No reverse order was found in this reading; that is not a concurrency proof (see §Known limits).
4. **Atomicity.** Business rows, audit rows, idempotency rows, and outbox rows are written in the same transaction (see `JdbcIdentityStore.createRegistration`, `issueAuthToken`, `JdbcAcademicStore.createUser`, announcement/topic stores). Any exception rolls back all of them.
5. **Idempotency.** `Idempotency-Key` is required for create/transition operations; key length 8–128 is validated in controllers/use cases. `command_idempotency` records live 86 400 s. A replay with a different request hash throws `IdempotencyKeyReuse` → `AcademicIdempotencyConflict` (409). Attempt/submission submit and feedback publish re-check idempotency after locking the target row.
6. **Revisions.** Optimistic revisions are checked under `FOR UPDATE` (or `WHERE revision=?`) and conflicts map to 409 with `details.currentRevision` when available.
7. **Remote snapshot calls.** `ManageAssessments.capture` and `ManageTopics.create` call the Questions private interface outside the database transaction and validate revision/content hash/image IDs before the store write.
8. **Outbox.** `OutboxPublisher.publishAvailable` opens a `TransactionTemplate`, selects `for update skip locked limit 25`, publishes with `confirmSelect` + mandatory return, then marks `published_at`. A crash between confirm and update is the documented at-least-once window. Failures increment attempts with exponential backoff (1 s → 256 s; the shift is capped at 8) and set `failed_at` at attempt 5. Redrive resets attempts/next attempt/failed_at only for unpublished, failed rows.
9. **Read paths.** Query methods are `@Transactional(readOnly=true)`, and some still call `lockAuthInvariant()` for session/authorization consistency (`JdbcAttemptStore.attempts/attempt/result`, `JdbcSubmissionStore.submissions/submission/pendingFeedbackCount`). Pagination is bounded 1..100; sorting is allow-listed.

### Per-write quick reference

| Write | Transaction boundary | Required locks / checks | Remote call |
| --- | --- | --- | --- |
| register | `JdbcIdentityStore.createRegistration` | no advisory lock; INSERT user + auth_token + outbox + registration audit in one transaction; uniqueness by normalized email | none |
| login | `JdbcIdentityStore.rotateSession` | advisory → users FOR UPDATE → session rotation; password hash equality | none |
| logout | `JdbcIdentityStore.revokeSession` | advisory → session revocation | none |
| token request | `JdbcIdentityStore.issueAuthToken` | user FOR UPDATE → invalidate prior token → insert token+outbox | none |
| email confirmation | `JdbcIdentityStore.confirmEmail` | advisory → token read → user FOR UPDATE → classes/enrollments FOR UPDATE → token FOR UPDATE | none |
| password reset | `JdbcIdentityStore.resetPassword` | advisory → token read → user FOR UPDATE → token FOR UPDATE → revoke sessions | none |
| profile update | `JdbcIdentityStore.updateProfile` | advisory → session+user FOR UPDATE → session FOR UPDATE → revision | none |
| academic user create | `JdbcAcademicStore.createUser` | INSERT only, no advisory lock, no FOR UPDATE; uniqueness by normalized email | none |
| academic user update/role/deactivate | `JdbcAcademicStore.updateUser/changeRole/deactivate/withdraw` | advisory → row FOR UPDATE for role/deactivate; `updateUser` advances revision by compare-and-set → session revocation where role/email changes | none |
| class create/update/close/reopen | `JdbcAcademicStore.createClass/updateClass/setClassActive` | no advisory lock; INSERT or `UPDATE … WHERE revision=?` | none |
| enrollment create/transition | `JdbcAcademicStore.*` | user/class/enrollment FOR UPDATE in documented order → revision | none |
| subject/module write | `JdbcSubjectStore.*` | advisory → SQL authorization in store → revision | none |
| file/material write | `JdbcContentStore.*` | material FOR UPDATE → staged file FOR UPDATE; idempotency | none |
| assessment create/update/publish | `JdbcAssessmentStore.*` | advisory → class/user/subject/module FOR UPDATE → assessment FOR UPDATE → revision → idempotency | snapshot fetch before transaction |
| attempt start/save/submit | `JdbcAttemptStore.*` | advisory → session check → assessment FOR SHARE → attempt FOR UPDATE → revision/idempotency | none |
| submission create/update/submit | `JdbcSubmissionStore.*` | advisory → submission FOR UPDATE → files FOR UPDATE ordered by id → deadline re-check | none |
| feedback save/publish | `JdbcSubmissionStore.*` | advisory → class/user FOR UPDATE → submission FOR UPDATE → revision | none |
| announcement create/update/withdraw | `JdbcAnnouncementStore.*` | advisory → class/subject FOR UPDATE → announcement FOR UPDATE → revision; idempotency on create only | none |
| topic create/reply/read/solution/vote | `JdbcTopicStore.*` | advisory → authorization + replay lookup → topic/class/subject/attachment locks; idempotency | question snapshot before create |
| attendance create/update | `JdbcAttendanceStore.*` | advisory → classes/users/subjects FOR UPDATE (via `requireWriter(…, lock = true)`) → attendance FOR UPDATE → revision/idempotency | none |
| outbox publish/redrive | `OutboxPublisher` / `OutboxRedrive` | select FOR UPDATE SKIP LOCKED; confirms; at-least-once | RabbitMQ publish inside DB transaction |

## Declaration-to-destination map

Current package → target feature package (R-02). R-01 first splits multi-type files **without changing packages**. 26 of the 54 files below declare more than one top-level type and are split in R-01; the other 28 are single-type and only move in R-02.

| Current file (all under `services/core/src/main/kotlin/br/com/ajadigital/core/`) | Declared top-level types | Target feature |
| --- | --- | --- |
| `AuthController.kt` | `RegisterRequest`, `AuthController`, `LoginRequest`, `EmailRequest`, `SelfController`, `ProfileController`, `InvalidInternalServiceCredential`, `InternalSessionResponse`, `InternalPracticeRequest`, `InternalPracticeResponse`, `InternalSessionController`, `ProfileEditRequest`, `AuthExceptionHandler` (+ `createSessionCookie`) | `identity` |
| `AnnouncementController.kt` | `AnnouncementCreateRequest`, `AnnouncementEditRequest`, `AnnouncementRevisionRequest`, `AnnouncementController` | `announcements/adapters/in/controllers` |
| `DashboardController.kt` | `DashboardController` | `dashboard/adapters/in/controllers` |
| `application/identity/RegisterStudent.kt` | `RegistrationConflict`, `RegistrationCommand`, `RegistrationResult`, `RegisterStudent`, `EncryptedOutboxPayload`, `OutboxEncryption` | `identity` |
| `application/identity/AuthenticateStudent.kt` | `InvalidCredentials`, `SessionRequired`, `LoginCommand`, `AuthenticatedSession`, `AuthenticateStudent`, `SessionResponse` (+ `SessionRecord.toResponse`) | `identity` |
| `application/identity/ManageAuthTokens.kt` | `AuthTokenInvalid`, `AuthTokenExpired`, `TokenCommand`, `PasswordResetCommand`, `AcceptedResponse`, `RequestAuthToken`, `ResetPassword` (+ `AuthTokenResult.toUserOrThrow`) | `identity` |
| `application/identity/UpdateOwnProfile.kt` | `ProfileRevisionConflict`, `ProfileEditCommand`, `UpdateOwnProfile` | `identity` |
| `domain/identity/User.kt`, `domain/identity/EmailAddress.kt` | `User`, `EmailAddress` | `identity/domain/{models,policies}` |
| `ports/out/IdentityStore.kt` | `RegistrationRecords`, `IdentityStore`, `LoginIdentity`, `SessionRecord`, `AuthTokenIssue`, `AuthTokenResult`, `TokenFailure`, `ProfileUpdateResult` (nested subtypes) | `identity/domain/ports/out` |
| `ports/out/PasswordHasher.kt` | `PasswordHasher` | `identity/domain/ports/out` |
| `ports/out/IdempotencyStore.kt` | `IdempotencyKeyReuse`, `IdempotencyStore` | `shared/domain/ports/out` (technical capability) |
| `adapter/out/persistence/JdbcIdentityStore.kt` | `JdbcIdentityStore` | `identity/adapters/out/persistence` |
| `adapter/out/persistence/JdbcIdempotencyStore.kt` | `JdbcIdempotencyStore` | `shared/adapters/out/persistence` |
| `adapter/out/security/Argon2PasswordHasher.kt` | `Argon2PasswordHasher` | `identity/adapters/out/security` |
| `AcademicController.kt` | request DTOs, `AcademicController`, `AcademicExceptionHandler` | `classroom`/`curriculum`/`forum`/`attendance` controllers + handlers |
| `application/academic/ManageAcademics.kt` | `AcademicForbidden`, `AcademicNotFound`, `AcademicConflict`, `AcademicIdempotencyConflict`, `AcademicActor`, `ManageAcademics` | `classroom` |
| `ports/out/AcademicStore.kt` | `AcademicPage`, `ProvisionedIdentity`, `AcademicStore` | `classroom/domain/ports/out` (`AcademicPage` shared model) |
| `adapter/out/persistence/JdbcAcademicStore.kt` | `JdbcAcademicStore` | `classroom/adapters/out/persistence` |
| `application/academic/ManageSubjects.kt` | `UnknownPublication`, `InvalidAcademicReference`, `ManageSubjects` | `curriculum` |
| `ports/out/SubjectStore.kt` | `SubjectStore` | `curriculum/domain/ports/out` |
| `adapter/out/persistence/JdbcSubjectStore.kt` | `JdbcSubjectStore` | `curriculum/adapters/out/persistence` |
| `application/academic/ManageContent.kt` | `ManageContent` | `materials` |
| `ports/out/ContentStore.kt` | `ContentStore`, `DashboardContent`, `DownloadedFile`, `MaterialCreate` | `materials/domain/{ports/out,models}` |
| `ContentController.kt` | `FileUploadSessionFilter`, `MaterialBatchRequest`, `MaterialCreateRequest`, `MaterialEditRequest`, `ProgressCommandRequest`, `ContentController` | `materials/adapters/in/{controllers,filters}` |
| `adapter/out/persistence/JdbcContentStore.kt` | `JdbcContentStore` | `materials/adapters/out/persistence` |
| `application/academic/ManageAssessments.kt` | `QuestionSnapshotNotFound`, `QuestionsUnavailable`, `ManageAssessments` | `assessment` |
| `application/academic/ManageAttempts.kt` | `InvalidAttemptAnswers`, `ManageAttempts` | `assessment` |
| `application/academic/ManageSubmissions.kt` | `InvalidSubmissionFeedback`, `ManageSubmissions` | `assessment` |
| `AssessmentController.kt` | `AssessmentController`, `AttemptController`, `SubmissionController` | `assessment/adapters/in/controllers` |
| `ports/out/AssessmentStore.kt` | `AssessmentDraft`, `AssessmentQuestion`, `AssessmentPage`, `AssessmentStore`, `AttemptStore`, `SubmissionAttachment`, `SubmissionPage`, `SubmissionStore`, `QuestionSnapshotProvider`, `BankQuestionSnapshot`, `BankQuestionImage` | `assessment/domain/{ports/out,models}` |
| `adapter/out/persistence/JdbcAssessmentStore.kt`, `JdbcAttemptStore.kt`, `JdbcSubmissionStore.kt` | three adapters | `assessment/adapters/out/persistence` |
| `adapter/out/questions/HttpQuestionSnapshotProvider.kt` | `SnapshotRequest`, `SnapshotResponse`, `SnapshotDto`, `ImageDto`, `HttpQuestionSnapshotProvider` | `assessment/adapters/out/questions` |
| `application/academic/ManageTopics.kt` | `InvalidTopicReference`, `ManageTopics` | `forum` |
| `ports/out/TopicStore.kt` | `TopicDraft`, `TopicStore` | `forum/domain/{models,ports/out}` |
| `adapter/out/persistence/JdbcTopicStore.kt` | `JdbcTopicStore` | `forum/adapters/out/persistence` |
| `application/academic/ManageAnnouncements.kt` | `ManageAnnouncements` | `announcements` |
| `ports/out/AnnouncementStore.kt` | `AnnouncementOccurrence`, `AnnouncementDraft`, `AnnouncementStore` | `announcements/domain/{models,ports/out}` |
| `adapter/out/persistence/JdbcAnnouncementStore.kt` | `JdbcAnnouncementStore` | `announcements/adapters/out/persistence` |
| `application/academic/ManageAttendance.kt` | `InvalidAttendanceRecords`, `ManageAttendance` | `attendance` |
| `ports/out/AttendanceStore.kt` | `AttendanceDraft`, `AttendanceStore` | `attendance/domain/{models,ports/out}` |
| `adapter/out/persistence/JdbcAttendanceStore.kt` | `JdbcAttendanceStore` | `attendance/adapters/out/persistence` |
| `application/academic/ManageDashboard.kt` | `ManageDashboard` | `dashboard` |
| `HealthController.kt` | `HealthController` | `health/adapters/in/controllers` |
| `application/health/CheckServiceHealth.kt` | `CheckServiceHealth` | `health/domain/usecases` |
| `domain/health/ServiceReadiness.kt` | `ServiceReadiness` | `health/domain/models` |
| `ports/out/HealthProbe.kt` | `HealthProbe` | `health/domain/ports/out` |
| `adapter/out/health/DatabaseAndBrokerHealthProbe.kt` | `DatabaseAndBrokerHealthProbe` | `health/adapters/out` |
| `adapter/out/messaging/OutboxPublisher.kt` | `UnroutableOutboxEvent`, `PendingOutboxEvent`, `OutboxPublisher`, `OutboxRedrive`, `OutboxOperationsController` | `outbox` (split per R-06) |
| `adapter/out/messaging/IdentityEventsTopology.kt` | `IdentityEventsTopology` (+ `EVENTS_EXCHANGE` const) | `outbox/adapters/out/messaging` |
| `OriginFilter.kt` | `OriginFilter` | `shared/adapters/in/web/filters` (ownership confirmed in R-00: pure HTTP boundary, no identity state) |
| `CoreApplication.kt` | `CoreApplication` (+ `main`) | `configuration` |

### Test declarations

| Current file | Types | Treatment |
| --- | --- | --- |
| `CoreApplicationTests.kt` | `MutableTestClock`, `MutableTestClockConfiguration`, `EmailQueueTopologyTestConfiguration`, `TestQuestionSnapshotProvider`, `AssessmentSnapshotTestConfiguration`, `CoreOpenApiCoverageFilter`, `CoreOpenApiCoverageConfiguration`, `CoreApplicationTests` | split support types into their own files and split the 111 tests into per-feature integration classes, keeping every case and assertion; preserve aggregate OpenAPI coverage as described in §Decisions approved for R-01 |
| `ArchitectureTests.kt` | `ArchitectureTests` | update package discovery in R-02, harden in R-09 |
| `HealthControllerTests.kt`, `OriginFilterTests.kt`, `SessionCookieTests.kt` | one class each | move beside feature adapters in R-02 |
| `adapter/out/questions/HttpQuestionSnapshotProviderTests.kt` | one class | move with its adapter in R-02 |

## Decisions approved for R-01

R-01's plan text already requires splitting `CoreApplicationTests.kt` into per-feature integration test classes. Because that cannot be a pure file move, the repository owner approved the following on 2026-11-13, before any R-01 edit. They are recorded here because R-00 is the shared reference for later issues.

1. **Confirmed, not a deviation:** the per-feature test split stays in R-01, as the plan already requires.
2. **Approved deviation:** the aggregate OpenAPI coverage mechanism changes. Today the observation map is a per-instance field of `CoreOpenApiCoverageFilter` and the assertion runs in `@AfterAll` of the single suite, gated by `B16_OPENAPI_COVERAGE=true`, which only `scripts/acceptance-b16.ps1` sets. After the split, observations must live in shared state and the assertion must run once for the whole test run, or `acceptance-b16.ps1` silently stops covering anything while plain `gradlew test` stays green.
3. **Approved deviation:** test helpers move into one shared base test class rather than being duplicated per feature class; this is the only new type the split adds.
4. **Approved deviation:** top-level `private` declarations that become cross-file after a split widen to `internal`. This is the only non-move edit in main sources; the affected names are the six outbox/snapshot DTO types plus `AuthTokenResult.toUserOrThrow`.

These are recorded so R-01 does not silently re-scope R-00's characterization or the plan's acceptance criteria.

## Known limits of this characterization

- No production secrets or runtime state are recorded.
- The baseline proves the current suite as executed on this machine only; it is not evidence for any later R-issue.
- `CoreApplicationTests` is a single 5 721-line suite with class-level `@DirtiesContext(AFTER_CLASS)`. Per-class context teardown and the per-instance coverage map are why a naive split would break the aggregate check and change context reuse; R-01 must handle both, per §Decisions approved for R-01.
- Transactions above are as declared by `@Transactional` and observed SQL. Lock-order claims come from reading the SQL, not from concurrent stress testing; runtime deadlock behavior was not tested in R-00.

## Review record

Two independent read-only reviewers inspected this document and the claim behind it; both rechecked the fixes.

| Round | ECC reviewer | Ponytail reviewer |
| --- | --- | --- |
| 1 | REQUEST CHANGES — overstated advisory-lock rule, wrong `register` and academic per-write rows, missing declaration rows and wrong paths, backoff cap, line count | REQUEST CHANGES — missing declaration rows, silent R-01 re-scope, wrong coverage rationale, unproven lock-order absolute, duplicated lock prose |
| 2 | REQUEST CHANGES — attendance `create` lock missed (reached through `requireWriter(…, lock = true)`), "read-only paths" exclusion self-contradictory, session-exception list incomplete | REQUEST CHANGES — header pointed at a missing section, approver not named, plan contradicted its own deviations, exception count wrong |
| 3 | **not run** — the reviewer session hit the provider's free-usage limit. Its four round-2 points were verified locally instead (`JdbcAttendanceStore.kt:76,90,146` for the lock reached via `requireWriter`; rule 2's "read-only paths" exclusion removed and rule 9 restated; `AuthController.kt:103,107,110,114` for the four token routes; `## Review record` present and referenced from the header) | **APPROVE** — all four targeted points pass with no new findings |

All round-1 and round-2 findings were reproduced against the code before being accepted; none were dismissed. Round 3 has Ponytail's approval and a local verification in place of the ECC recheck that could not run.
