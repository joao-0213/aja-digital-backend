package br.com.ajadigital.core.assessment.adapters.out.persistence

import br.com.ajadigital.core.assessment.application.models.AttemptStartCommand

import br.com.ajadigital.core.assessment.application.models.AttemptListQuery

import br.com.ajadigital.core.assessment.application.models.AttemptGetQuery

import br.com.ajadigital.core.assessment.application.models.AttemptAnswerSaveCommand

import br.com.ajadigital.core.assessment.application.models.AttemptSubmitCommand

import br.com.ajadigital.core.assessment.application.models.AttemptResultQuery

import br.com.ajadigital.core.assessment.application.models.PracticeBlockedQuery

import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentConflict
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentForbidden
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.assessment.domain.exceptions.InvalidAttemptAnswers
import br.com.ajadigital.core.assessment.domain.models.AttemptPage
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.assessment.domain.models.AttemptReceipt
import br.com.ajadigital.core.assessment.domain.models.AttemptResult
import br.com.ajadigital.core.assessment.domain.models.AttemptState
import br.com.ajadigital.core.assessment.domain.models.AttemptQuestion
import br.com.ajadigital.core.assessment.domain.policies.AttemptScoringPolicy
import br.com.ajadigital.core.assessment.domain.policies.DeadlinePolicy
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.adapters.serialization.AttemptRepresentation
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcAttemptStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
) : AttemptStoreOutputPort {
    @Transactional
    override fun practiceBlocked(request: PracticeBlockedQuery): Boolean {
        val (actorId, role, sessionHash, questionId) = request
        lockAuthInvariant()
        requireSession(actorId, sessionHash)
        if (role != "student") return false
        return jdbc.queryForObject("""SELECT EXISTS(
            SELECT 1 FROM core.attempts x
            JOIN core.assessments a ON a.id=x.assessment_id
            JOIN core.modules m ON m.id=a.module_id
            JOIN core.subjects s ON s.id=m.subject_id
            JOIN core.classes c ON c.id=s.class_id
            WHERE x.user_id=? AND x.submitted_at IS NULL
              AND a.kind='quiz' AND a.status='published' AND m.withdrawn_at IS NULL AND c.active
              AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id
                WHERE e.class_id=s.class_id AND e.student_id=x.user_id AND e.status='active' AND u.active AND u.role='student'
                  AND u.email_confirmed_at IS NOT NULL AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id)
                  AND (e.enrolled_at IS NULL OR a.deadline IS NULL OR e.enrolled_at<=a.deadline))
              AND x.question_order @> jsonb_build_array(?)
              AND EXISTS(SELECT 1 FROM jsonb_array_elements(x.questions) snapshot WHERE snapshot->>'id'=?)
        )""", Boolean::class.java, actorId, questionId, questionId) ?: false
    }

    @Transactional
    override fun start(request: AttemptStartCommand): AttemptRecord {
        val (actorId, role, sessionHash, assessmentId, key, requestHash) = request
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "student")
        val assessment = studentAssessment(actorId, assessmentId) ?: throw AssessmentNotFound()
        replay(actorId, "startAttempt", "/attempts", key, requestHash)?.let { return AttemptRepresentation.readAttempt(it) }
        val existing = jdbc.query("SELECT id FROM core.attempts WHERE assessment_id=? AND user_id=?", { rs, _ -> rs.getString(1) }, assessmentId, actorId).firstOrNull()
        if (existing != null) {
            val attempt = authorizedAttempt(actorId, role, sessionHash, existing) ?: throw AssessmentNotFound()
            val record = attempt.toRecord()
            idempotency.saveEncoded(FindIdempotencyDto(actorId, "startAttempt", "/attempts", key, requestHash), mapper.writeValueAsString(AttemptRepresentation.attempt(record)))
            return record
        }
        if (!DeadlinePolicy.isBeforeDeadline(assessment.deadline, assessment.allowLate, clock.instant())) throw AssessmentConflict()
        val questions = jdbc.query("SELECT question_id,snapshot::text FROM core.assessment_questions WHERE assessment_id=? ORDER BY position", { rs, _ ->
            @Suppress("UNCHECKED_CAST")
            val snapshot = mapper.readValue(rs.getString("snapshot"), Map::class.java) as Map<String, Any?>
            snapshot + ("id" to rs.getString("question_id"))
        }, assessmentId)
        if (questions.isEmpty()) throw AssessmentConflict()
        val order = questions.map { it["id"] as String }
        val now = Timestamp.from(clock.instant())
        val attemptId = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.attempts(id,assessment_id,user_id,revision,created_at,updated_at,updated_by,started_at,answers,question_order,questions,questions_updated_at)
            VALUES(?,?,?,1,?,?,?,?,'{}'::jsonb,?::jsonb,?::jsonb,?)""", attemptId, assessmentId, actorId, now, now, actorId, now,
            mapper.writeValueAsString(order), mapper.writeValueAsString(questions), assessment.questionsUpdatedAt?.let(Timestamp::from))
        val attempt = authorizedAttempt(actorId, role, sessionHash, attemptId) ?: throw AssessmentNotFound()
        val record = attempt.toRecord()
        idempotency.saveEncoded(FindIdempotencyDto(actorId, "startAttempt", "/attempts", key, requestHash), mapper.writeValueAsString(AttemptRepresentation.attempt(record)))
        return record
    }

    @Transactional(readOnly = true)
    override fun attempts(request: AttemptListQuery): AttemptPage {
        val (actorId, role, sessionHash, page, pageSize, sort, assessmentId, studentId) = request
        lockAuthInvariant()
        requireSession(actorId, sessionHash)
        if (role == "student" && studentId != null && studentId != actorId) throw AssessmentNotFound()
        val clauses = mutableListOf("(${accessClause(role)})")
        val args = mutableListOf<Any>()
        when (role) {
            "student", "teacher" -> args += actorId
            "admin" -> Unit
            else -> throw AssessmentForbidden()
        }
        assessmentId?.let { clauses += "x.assessment_id=?"; args += it }
        if (role != "student" && studentId != null) { clauses += "x.user_id=?"; args += studentId }
        val where = clauses.joinToString(" AND ")
        val from = "FROM core.attempts x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.users u ON u.id=x.user_id"
        val total = jdbc.queryForObject("SELECT count(*) $from WHERE $where", Long::class.javaObjectType, *args.toTypedArray()) ?: 0L
        val order = if (sort == "-id") "x.id DESC" else "x.id"
        val rows = jdbc.query("SELECT x.*,a.title AS assessment_title,a.deadline,a.allow_late,a.max_grade,u.name AS student_name FROM core.attempts x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.users u ON u.id=x.user_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id WHERE $where ORDER BY $order LIMIT ? OFFSET ?", { rs, _ -> rs.attemptRow() }, *(args + listOf(pageSize, (page - 1) * pageSize)).toTypedArray())
        return AttemptPage(rows.map { it.toRecord() }, page, pageSize, total)
    }

    @Transactional(readOnly = true)
    override fun attempt(request: AttemptGetQuery): AttemptRecord? {
        val (actorId, role, sessionHash, id) = request
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        return authorizedAttempt(actorId, role, sessionHash, id)?.toRecord()
    }

    @Transactional
    override fun saveAnswers(request: AttemptAnswerSaveCommand): AttemptRecord? {
        val actorId = request.actorId
        val role = request.role
        val sessionHash = request.sessionHash
        val id = request.id
        val expectedRevision = request.expectedRevision
        val answers = request.answers
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorizedAttempt(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        if (current.state is AttemptState.Submitted) throw AssessmentConflict(current.revision)
        if (current.revision != expectedRevision) throw AssessmentConflict(current.revision)
        if (!DeadlinePolicy.isBeforeDeadline(current.deadline, current.allowLate, clock.instant())) throw AssessmentConflict(current.revision)
        val optionCounts = current.questions.associate { it.id to checkNotNull(it.options).size }
        if (!AttemptScoringPolicy.areAnswersValid(answers, optionCounts)) throw InvalidAttemptAnswers()
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.attempts SET answers=?::jsonb,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", mapper.writeValueAsString(answers), now, actorId, id, expectedRevision) != 1) throw AssessmentConflict(current.revision)
        return authorizedAttempt(actorId, role, sessionHash, id)?.toRecord()
    }

    @Transactional
    override fun submit(request: AttemptSubmitCommand): AttemptResult? {
        val (actorId, role, sessionHash, id, key, requestHash, expectedRevision) = request
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorizedAttempt(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        replay(actorId, "submitAttempt", "/attempts/$id/submit", key, requestHash)?.let { return AttemptRepresentation.readResult(it) }
        if (current.state is AttemptState.Submitted) {
            val result = attemptResult(current)
            idempotency.saveEncoded(FindIdempotencyDto(actorId, "submitAttempt", "/attempts/$id/submit", key, requestHash), mapper.writeValueAsString(AttemptRepresentation.result(result)))
            return result
        }
        if (current.revision != expectedRevision) throw AssessmentConflict(current.revision)
        if (!DeadlinePolicy.isBeforeDeadline(current.deadline, current.allowLate, clock.instant())) throw AssessmentConflict(current.revision)
        val questions = current.questions
        val answers = current.answers
        val scored = AttemptScoringPolicy.calculateScore(
            questions.map { it.id to it.correct },
            answers,
            current.maxGrade,
        )
        val maxScore = scored.maxScore
        val score = scored.score
        val now = Timestamp.from(clock.instant())
        val receiptId = UUID.randomUUID().toString()
        if (jdbc.update("UPDATE core.attempts SET submitted_at=?,receipt_id=?,score=?,max_score=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", now, receiptId, score, maxScore, now, actorId, id, expectedRevision) != 1) throw AssessmentConflict(current.revision)
        val submitted = authorizedAttempt(actorId, role, sessionHash, id) ?: return null
        val result = attemptResult(submitted)
        idempotency.saveEncoded(FindIdempotencyDto(actorId, "submitAttempt", "/attempts/$id/submit", key, requestHash), mapper.writeValueAsString(AttemptRepresentation.result(result)))
        return result
    }

    @Transactional(readOnly = true)
    override fun result(request: AttemptResultQuery): AttemptResult? {
        val (actorId, role, sessionHash, id) = request
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        val current = authorizedAttempt(actorId, role, sessionHash, id) ?: return null
        return if (current.state is AttemptState.Submitted) attemptResult(current) else null
    }

    private fun studentAssessment(actorId: String, id: String): StudentAssessment? {
        val accepted = jdbc.query("""SELECT a.deadline,a.allow_late,a.questions_updated_at
            FROM core.assessments a JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
            WHERE a.id=? AND a.kind='quiz' AND a.status='published' AND m.withdrawn_at IS NULL AND c.active
              AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id
                WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL
                  AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id) AND (e.enrolled_at IS NULL OR a.deadline IS NULL OR e.enrolled_at<=a.deadline))
            FOR SHARE OF a""", { rs, _ -> StudentAssessment(rs.getTimestamp("deadline")?.toInstant(), rs.getBoolean("allow_late"), rs.getTimestamp("questions_updated_at")?.toInstant()) }, id, actorId).firstOrNull()
        return accepted
    }

    private fun authorizedAttempt(actorId: String, role: String, sessionHash: String, id: String, forUpdate: Boolean = false): AttemptPersistenceRow? {
        val rows = jdbc.query("""SELECT x.*,a.title AS assessment_title,a.deadline,a.allow_late,a.max_grade,u.name AS student_name
            FROM core.attempts x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id
            JOIN core.subjects s ON s.id=m.subject_id JOIN core.users u ON u.id=x.user_id
            WHERE x.id=? AND ${accessClause(role)}${if (forUpdate) " FOR UPDATE OF x" else ""}""", { rs, _ -> rs.attemptRow() }, *(listOf(id) + when (role) { "student", "teacher" -> listOf(actorId); "admin" -> emptyList(); else -> return null }).toTypedArray())
        return rows.firstOrNull()
    }

    private fun accessClause(role: String) = when (role) {
        "student" -> "x.user_id=? AND a.status='published' AND m.withdrawn_at IS NULL AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users su ON su.id=e.student_id JOIN core.classes c ON c.id=e.class_id WHERE e.class_id=s.class_id AND e.student_id=x.user_id AND e.status='active' AND su.active AND su.role='student' AND su.email_confirmed_at IS NOT NULL AND c.active AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id) AND (e.enrolled_at IS NULL OR a.deadline IS NULL OR e.enrolled_at<=a.deadline))"
        "teacher" -> "x.submitted_at IS NOT NULL AND EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users tu ON tu.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND tu.active AND tu.role='teacher')"
        "admin" -> "x.submitted_at IS NOT NULL"
        else -> "false"
    }

    private fun attemptResult(row: AttemptPersistenceRow): AttemptResult {
        return AttemptResult(
            attempt = row.toRecord(),
            receipt = AttemptReceipt(checkNotNull(row.receiptId), row.id, (row.state as AttemptState.Submitted).submittedAt.toString()),
            score = checkNotNull(row.score).toDouble(),
            maxScore = checkNotNull(row.maxScore).toDouble(),
            questions = row.questions,
        )
    }

    private fun ResultSet.attemptRow(): AttemptPersistenceRow {
        @Suppress("UNCHECKED_CAST")
        val snapshotRows = mapper.readValue(getString("questions"), List::class.java) as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val answers = mapper.readValue(getString("answers"), Map::class.java) as Map<String, Int?>
        @Suppress("UNCHECKED_CAST")
        val order = mapper.readValue(getString("question_order"), List::class.java) as List<String>
        @Suppress("UNCHECKED_CAST")
        val submittedAt = getTimestamp("submitted_at")?.toInstant()
        return AttemptPersistenceRow(
            id = getString("id"), revision = getInt("revision"), createdAt = getTimestamp("created_at").toInstant().toString(),
            updatedAt = getTimestamp("updated_at").toInstant().toString(), updatedBy = getString("updated_by"),
            assessmentId = getString("assessment_id"), assessmentTitle = getString("assessment_title"), userId = getString("user_id"),
            studentName = getString("student_name"), startedAt = getTimestamp("started_at").toInstant().toString(),
            state = submittedAt?.let { AttemptState.Submitted(it) } ?: AttemptState.InProgress,
            answers = answers, questionOrder = order, questions = snapshotRows.map(AttemptRepresentation::readQuestion),
            questionsUpdatedAt = getTimestamp("questions_updated_at")?.toInstant()?.toString(), receiptId = getString("receipt_id"),
            score = getBigDecimal("score"), maxScore = getBigDecimal("max_score"), deadline = getTimestamp("deadline")?.toInstant(),
            allowLate = getBoolean("allow_late"), maxGrade = getBigDecimal("max_grade"),
        )
    }

    private fun AttemptPersistenceRow.toRecord() = AttemptRecord(
        id, revision, createdAt, updatedAt, updatedBy, assessmentId, assessmentTitle, userId, studentName, startedAt,
        state, answers, questionOrder, questions.map(AttemptRepresentation::promptQuestion), questionsUpdatedAt,
    )

    private data class AttemptPersistenceRow(
        val id: String,
        val revision: Int,
        val createdAt: String,
        val updatedAt: String,
        val updatedBy: String?,
        val assessmentId: String,
        val assessmentTitle: String,
        val userId: String,
        val studentName: String?,
        val startedAt: String,
        val state: AttemptState,
        val answers: Map<String, Int?>,
        val questionOrder: List<String>,
        val questions: List<AttemptQuestion>,
        val questionsUpdatedAt: String?,
        val receiptId: String?,
        val score: BigDecimal?,
        val maxScore: BigDecimal?,
        val deadline: java.time.Instant?,
        val allowLate: Boolean,
        val maxGrade: BigDecimal?,
    )

    private data class StudentAssessment(val deadline: java.time.Instant?, val allowLate: Boolean, val questionsUpdatedAt: java.time.Instant?)

    private fun requireSession(actorId: String, hash: String, vararg roles: String) {
        val accepted = roles.toList().ifEmpty { listOf("admin", "teacher", "student") }
        val placeholders = accepted.joinToString(",") { "?" }
        if (jdbc.query("SELECT 1 FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getInt(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).isEmpty()) throw AssessmentForbidden()
    }

    @Suppress("UNCHECKED_CAST")
    private fun readReceipt(value: String): Map<String, Any?> = mapper.readValue(value, Map::class.java) as Map<String, Any?>

    private fun replay(actorId: String, operation: String, target: String, key: String, hash: String): Map<String, Any?>? = try {
        idempotency.findEncoded(FindIdempotencyDto(actorId, operation, target, key, hash))?.let(::readReceipt)
    } catch (_: IdempotencyKeyReuse) { throw br.com.ajadigital.core.assessment.domain.exceptions.AssessmentIdempotencyConflict() }

    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }

}
