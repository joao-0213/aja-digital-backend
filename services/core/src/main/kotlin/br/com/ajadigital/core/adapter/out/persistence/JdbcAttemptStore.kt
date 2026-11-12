package br.com.ajadigital.core.adapter.out.persistence

import br.com.ajadigital.core.application.academic.AcademicConflict
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.application.academic.InvalidAttemptAnswers
import br.com.ajadigital.core.ports.out.AssessmentPage
import br.com.ajadigital.core.ports.out.AttemptStore
import br.com.ajadigital.core.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.ports.out.IdempotencyStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

@Repository
class JdbcAttemptStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val idempotency: IdempotencyStore,
) : AttemptStore {
    @Transactional
    override fun start(actorId: String, role: String, sessionHash: String, assessmentId: String, key: String, requestHash: String): Map<String, Any?> {
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "student")
        val assessment = studentAssessment(actorId, assessmentId) ?: throw AcademicNotFound()
        replay(actorId, "startAttempt", "/attempts", key, requestHash)?.let { return it }
        val existing = jdbc.query("SELECT id FROM core.attempts WHERE assessment_id=? AND user_id=?", { rs, _ -> rs.getString(1) }, assessmentId, actorId).firstOrNull()
        if (existing != null) {
            val attempt = authorizedAttempt(actorId, role, sessionHash, existing) ?: throw AcademicNotFound()
            val dto = attemptDto(attempt)
            idempotency.save(actorId, "startAttempt", "/attempts", key, requestHash, dto)
            return dto
        }
        if (!beforeDeadline(assessment["deadline"] as Timestamp?, assessment["allowLate"] as Boolean)) throw AcademicConflict()
        val questions = jdbc.query("SELECT question_id,snapshot::text FROM core.assessment_questions WHERE assessment_id=? ORDER BY position", { rs, _ ->
            @Suppress("UNCHECKED_CAST")
            val snapshot = mapper.readValue(rs.getString("snapshot"), Map::class.java) as Map<String, Any?>
            snapshot + ("id" to rs.getString("question_id"))
        }, assessmentId)
        if (questions.isEmpty()) throw AcademicConflict()
        val order = questions.map { it["id"] as String }
        val now = Timestamp.from(clock.instant())
        val attemptId = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.attempts(id,assessment_id,user_id,revision,created_at,updated_at,updated_by,started_at,answers,question_order,questions,questions_updated_at)
            VALUES(?,?,?,1,?,?,?,?,'{}'::jsonb,?::jsonb,?::jsonb,?)""", attemptId, assessmentId, actorId, now, now, actorId, now,
            mapper.writeValueAsString(order), mapper.writeValueAsString(questions), assessment["questionsUpdatedAt"])
        val attempt = authorizedAttempt(actorId, role, sessionHash, attemptId) ?: throw AcademicNotFound()
        val dto = attemptDto(attempt)
        idempotency.save(actorId, "startAttempt", "/attempts", key, requestHash, dto)
        return dto
    }

    @Transactional(readOnly = true)
    override fun attempts(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): AssessmentPage {
        lockAuthInvariant()
        requireSession(actorId, sessionHash)
        if (role == "student" && studentId != null && studentId != actorId) throw AcademicNotFound()
        val clauses = mutableListOf("(${accessClause(role)})")
        val args = mutableListOf<Any>()
        when (role) {
            "student", "teacher" -> args += actorId
            "admin" -> Unit
            else -> throw AcademicForbidden()
        }
        assessmentId?.let { clauses += "x.assessment_id=?"; args += it }
        if (role != "student" && studentId != null) { clauses += "x.user_id=?"; args += studentId }
        val where = clauses.joinToString(" AND ")
        val from = "FROM core.attempts x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.users u ON u.id=x.user_id"
        val total = jdbc.queryForObject("SELECT count(*) $from WHERE $where", Long::class.javaObjectType, *args.toTypedArray()) ?: 0L
        val order = if (sort == "-id") "x.id DESC" else "x.id"
        val rows = jdbc.query("SELECT x.*,a.title AS assessment_title,a.deadline,a.allow_late,a.max_grade,u.name AS student_name FROM core.attempts x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.users u ON u.id=x.user_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id WHERE $where ORDER BY $order LIMIT ? OFFSET ?", { rs, _ -> rs.attemptRow() }, *(args + listOf(pageSize, (page - 1) * pageSize)).toTypedArray())
        return AssessmentPage(rows.map(::attemptDto), page, pageSize, total)
    }

    @Transactional(readOnly = true)
    override fun attempt(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        return authorizedAttempt(actorId, role, sessionHash, id)?.let(::attemptDto)
    }

    @Transactional
    override fun saveAnswers(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, answers: Map<String, Int?>): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorizedAttempt(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        if (current["submittedAt"] != null) throw AcademicConflict(current["revision"] as Int)
        if (current["revision"] != expectedRevision) throw AcademicConflict(current["revision"] as Int)
        if (!beforeDeadline(current["deadline"] as Timestamp?, current["allowLate"] as Boolean)) throw AcademicConflict(current["revision"] as Int)
        val questions = current["questions"] as List<Map<String, Any?>>
        val byId = questions.associateBy { it["id"] as String }
        if (!answers.keys.all(byId::containsKey) || answers.any { (questionId, value) -> value != null && value !in (byId.getValue(questionId)["options"] as List<*>).indices }) throw InvalidAttemptAnswers()
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.attempts SET answers=?::jsonb,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", mapper.writeValueAsString(answers), now, actorId, id, expectedRevision) != 1) throw AcademicConflict(current["revision"] as Int)
        return authorizedAttempt(actorId, role, sessionHash, id)?.let(::attemptDto)
    }

    @Transactional
    override fun submit(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorizedAttempt(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        replay(actorId, "submitAttempt", "/attempts/$id/submit", key, requestHash)?.let { return it }
        if (current["submittedAt"] != null) {
            val response = attemptResult(current)
            idempotency.save(actorId, "submitAttempt", "/attempts/$id/submit", key, requestHash, response)
            return response
        }
        if (current["revision"] != expectedRevision) throw AcademicConflict(current["revision"] as Int)
        if (!beforeDeadline(current["deadline"] as Timestamp?, current["allowLate"] as Boolean)) throw AcademicConflict(current["revision"] as Int)
        val questions = current["questions"] as List<Map<String, Any?>>
        val answers = current["answers"] as Map<String, Int?>
        val correct = questions.count { q -> answers[q["id"] as String] == q["correct"] }
        val maxScore = current["maxGrade"] as BigDecimal? ?: BigDecimal.valueOf(questions.size.toLong())
        val score = maxScore.multiply(BigDecimal.valueOf(correct.toLong())).divide(BigDecimal.valueOf(questions.size.toLong()), 2, RoundingMode.HALF_UP)
        val now = Timestamp.from(clock.instant())
        val receiptId = UUID.randomUUID().toString()
        if (jdbc.update("UPDATE core.attempts SET submitted_at=?,receipt_id=?,score=?,max_score=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", now, receiptId, score, maxScore, now, actorId, id, expectedRevision) != 1) throw AcademicConflict(current["revision"] as Int)
        val submitted = authorizedAttempt(actorId, role, sessionHash, id) ?: return null
        val response = attemptResult(submitted)
        idempotency.save(actorId, "submitAttempt", "/attempts/$id/submit", key, requestHash, response)
        return response
    }

    @Transactional(readOnly = true)
    override fun result(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        val current = authorizedAttempt(actorId, role, sessionHash, id) ?: return null
        return if (current["submittedAt"] == null) null else attemptResult(current)
    }

    private fun studentAssessment(actorId: String, id: String): Map<String, Any?>? {
        val accepted = jdbc.query("""SELECT a.deadline,a.allow_late,a.questions_updated_at
            FROM core.assessments a JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
            WHERE a.id=? AND a.kind='quiz' AND a.status='published' AND m.withdrawn_at IS NULL AND c.active
              AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id
                WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL
                  AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id) AND (e.enrolled_at IS NULL OR a.deadline IS NULL OR e.enrolled_at<=a.deadline))
            FOR SHARE OF a""", { rs, _ -> mapOf("deadline" to rs.getTimestamp("deadline"), "allowLate" to rs.getBoolean("allow_late"), "questionsUpdatedAt" to rs.getTimestamp("questions_updated_at")) }, id, actorId).firstOrNull()
        return accepted
    }

    private fun authorizedAttempt(actorId: String, role: String, sessionHash: String, id: String, forUpdate: Boolean = false): Map<String, Any?>? {
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

    private fun attemptResult(row: Map<String, Any?>): Map<String, Any?> {
        val resolved = (row["questions"] as List<Map<String, Any?>>).map { it.filterKeys { key -> key in questionFields } }
        return mapOf(
            "attempt" to attemptDto(row),
            "receipt" to mapOf("id" to row["receiptId"], "resourceId" to row["id"], "submittedAt" to row["submittedAt"]),
            "score" to (row["score"] as BigDecimal).toDouble(),
            "maxScore" to (row["maxScore"] as BigDecimal).toDouble(),
            "questions" to resolved,
        )
    }

    private fun attemptDto(row: Map<String, Any?>): Map<String, Any?> = mapOf(
        "id" to row["id"], "revision" to row["revision"], "createdAt" to row["createdAt"], "updatedAt" to row["updatedAt"], "updatedBy" to row["updatedBy"],
        "assessmentId" to row["assessmentId"], "assessmentTitle" to row["assessmentTitle"], "userId" to row["userId"], "studentName" to row["studentName"],
        "startedAt" to row["startedAt"], "submittedAt" to row["submittedAt"], "answers" to row["answers"],
        "questionOrder" to row["questionOrder"],
        "questions" to (row["questions"] as List<Map<String, Any?>>).map { snapshot -> prompt(snapshot, snapshot["id"] as String) },
        "questionsUpdatedAt" to row["questionsUpdatedAt"],
    )

    private fun prompt(snapshot: Map<String, Any?>, id: String) = snapshot.filterKeys { it in questionFields - setOf("correct", "explanation") } + ("id" to id)

    private fun ResultSet.attemptRow(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val snapshotRows = mapper.readValue(getString("questions"), List::class.java) as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val answers = mapper.readValue(getString("answers"), Map::class.java) as Map<String, Int?>
        @Suppress("UNCHECKED_CAST")
        val order = mapper.readValue(getString("question_order"), List::class.java) as List<String>
        @Suppress("UNCHECKED_CAST")
        return mapOf(
            "id" to getString("id"), "revision" to getInt("revision"), "createdAt" to getTimestamp("created_at").toInstant().toString(),
            "updatedAt" to getTimestamp("updated_at").toInstant().toString(), "updatedBy" to getString("updated_by"),
            "assessmentId" to getString("assessment_id"), "assessmentTitle" to getString("assessment_title"), "userId" to getString("user_id"),
            "studentName" to getString("student_name"), "startedAt" to getTimestamp("started_at").toInstant().toString(),
            "submittedAt" to getTimestamp("submitted_at")?.toInstant()?.toString(), "answers" to answers, "questionOrder" to order,
            "questions" to snapshotRows, "questionsUpdatedAt" to getTimestamp("questions_updated_at")?.toInstant()?.toString(),
            "receiptId" to getString("receipt_id"), "score" to getBigDecimal("score"), "maxScore" to getBigDecimal("max_score"),
            "deadline" to getTimestamp("deadline"), "allowLate" to getBoolean("allow_late"), "maxGrade" to getBigDecimal("max_grade"),
        )
    }

    private fun beforeDeadline(deadline: Timestamp?, allowLate: Boolean): Boolean = allowLate || deadline == null ||
        clock.instant().atZone(brasilia).isBefore(deadline.toInstant().atZone(brasilia))

    private fun requireSession(actorId: String, hash: String, vararg roles: String) {
        val accepted = roles.toList().ifEmpty { listOf("admin", "teacher", "student") }
        val placeholders = accepted.joinToString(",") { "?" }
        if (jdbc.query("SELECT 1 FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getInt(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).isEmpty()) throw AcademicForbidden()
    }

    private fun replay(actorId: String, operation: String, target: String, key: String, hash: String): Map<String, Any?>? = try {
        idempotency.find(actorId, operation, target, key, hash)
    } catch (_: IdempotencyKeyReuse) { throw br.com.ajadigital.core.application.academic.AcademicIdempotencyConflict() }

    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }

    private companion object {
        val brasilia: ZoneId = ZoneId.of("America/Sao_Paulo")
        val questionFields = setOf("id", "subject", "topic", "difficulty", "institution", "year", "statement", "options", "image", "correct", "explanation")
    }
}
