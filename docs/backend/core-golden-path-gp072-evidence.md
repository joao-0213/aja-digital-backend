# GP-07.2 evidence — Shared technical contracts

## Preflight

- Starting revision `59bd0d6` on `codex/core-golden-path`. The Git index and Core service tree were clean. Pre-existing repository changes remain outside the issue.
- The preceding forced Core gate at this revision passed 237 tests in 47 suites with real PostgreSQL, RabbitMQ and Questions. Java 21, Docker and the Questions boot JAR are available. Database-backed suites run in sequence.
- The diagnostic audit found eight issues. Six arise from the remaining shared ports and their placement. The `SubmissionPolicy` URI import and `FieldChange<T>` syntax findings belong to GP-07.3.
- `graphify query 'TransactionRunnerOutputPort TransactionRunDto SpringTransactionRunner EncodedIdempotencyStoreOutputPort JdbcIdempotencyStore JsonSerializerOutputPort AcademicIdempotency AttendanceReceiptStore' --budget 650` located the shared port implementations and feature callers.
- The change reaches Core writes coordinated through `TransactionTemplate`, Academic commands, announcements and calendar notification creation, Attendance and Forum creation, attempt and submission receipts, and Identity and Academic token notifications. The public HTTP and private service contracts are the comparison baseline.

## Acceptance

| Criterion | Status |
| --- | --- |
| Direct transaction callback and its result, ordering, lock, propagation and rollback contracts | Verified |
| Encoded idempotency storage remains compatible with the prior SQL, retention, fingerprint, conflict and replay behavior | Verified |
| Feature-specific fingerprint and receipt codecs preserve bytes, field order, and optional fields | Verified |
| Token notifications preserve encryption inputs, plaintext schema, and the existing atomic write and outbox protocols | Verified |
| Focused and full live Core gates, Graphify, zero shared-contract findings and independent reviews | Verified |

## Implementation checkpoints

- The transaction port exposes the direct generic callback from its application package. `SpringTransactionRunner` still delegates to the existing `TransactionTemplate`, including for nullable results. `./gradlew compileKotlin compileTestKotlin --console=plain` passed after migrating its callers.
- The Forum, Attendance, attempt and submission receipt adapters use encoded idempotency. The shared JDBC store keeps its existing query, advisory lock, conditional update and 24-hour expiration. Intermediate compilation passed. An ordered source comparison against `59bd0d6` verified 83 unchanged SQL fragments in the four affected JDBC adapters.
- Academic, Announcements, Attendance and Forum fingerprints use request-specific signatures. The existing Jackson configuration still encodes the historical member names, values and ordering. Anonymous legacy request JSON in the attempt controller is normalized through the same existing transport conversion. The application sees the hash and the typed command.
- The token encryption adapter accepts the ordered Identity notification fields explicitly. An AES-GCM test decrypts with the existing event ID, type and version as authenticated data and compares plaintext with the prior `recipient`, `token`, `userId`, `purpose` representation. Ciphertext continues to use a new nonce for each event. The DTO has no generated token-bearing `toString`.
- `./gradlew architectureAudit --console=plain` exited 1 with exactly the two source-rule findings assigned to GP-07.3, and zero shared-contract findings.

## Focused validation

From `services/core`, the following exited 0 in 1m27s. JUnit recorded nine tests in seven suites with zero failures, errors or skips. The checks cover the legacy fingerprints and plaintext, the Attendance receipt through an encoded-port substitute and live PostgreSQL, the Forum replay recheck after contention, identity registration, and PostgreSQL rollback and successful nullable results.

```sh
./gradlew test \
  --tests br.com.ajadigital.core.shared.adapters.out.serialization.RequestFingerprintCompatibilityTests \
  --tests br.com.ajadigital.core.attendance.AttendanceReceiptStoreTests \
  --tests br.com.ajadigital.core.outbox.adapters.out.security.AesGcmOutboxPayloadProtectorTests \
  --tests br.com.ajadigital.core.PlatformIntegrationTests.springTransactionRunnerRollsBackAtomicallyOnFailure \
  --tests br.com.ajadigital.core.attendance.AttendanceIntegrationTests.attendanceCanBeCreatedAndReplayedWithoutDuplicatingTheClassSession \
  --tests br.com.ajadigital.core.forum.ForumIntegrationTests.topicReplayRechecksSessionAfterWaitingForIdempotencyLock \
  --tests br.com.ajadigital.core.identity.IdentityIntegrationTests.registrationCreatesStudentAndReturnsContractResponse \
  --console=plain
```

## Full gate

`./gradlew test build --rerun-tasks --console=plain` exited 0 in 11m43s. JUnit recorded 239 tests in 48 suites with zero failures, errors or skips, including real PostgreSQL, RabbitMQ and Questions process checks. The replay tests exercise both temporary idempotency rows and permanent receipts after expiration and a fresh Core process.

`graphify update .` completed from the repository root with 5,635 nodes and 15,755 edges. The local extractor still omits SQL because its parser is unavailable. The source comparison and PostgreSQL checks cover the affected SQL independently. `git diff --check` passed.

## Independent review

- ECC on GPT-6.1 Terra at medium reasoning approved transaction result handling, legacy fingerprints and receipt codecs, Forum recheck ordering, and token payload compatibility and redaction. No actionable findings.
- The registered PostgreSQL reviewer on GPT-6.1 Sol at medium reasoning approved lock order, bind values, authorization revalidation, retention, permanent receipts, rollback and outbox atomicity. Its independent check found 76 unchanged complete statements in execution order. No actionable findings.
