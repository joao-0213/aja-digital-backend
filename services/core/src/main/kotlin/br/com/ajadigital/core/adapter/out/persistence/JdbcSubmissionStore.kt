package br.com.ajadigital.core.adapter.out.persistence

import br.com.ajadigital.core.application.academic.AcademicConflict
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.application.academic.InvalidSubmissionFeedback
import br.com.ajadigital.core.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.ports.out.IdempotencyStore
import br.com.ajadigital.core.ports.out.SubmissionAttachment
import br.com.ajadigital.core.ports.out.SubmissionPage
import br.com.ajadigital.core.ports.out.SubmissionStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Repository
class JdbcSubmissionStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val idempotency: IdempotencyStore,
) : SubmissionStore {
    @Transactional
    override fun create(actorId: String, role: String, sessionHash: String, assessmentId: String, attachments: List<SubmissionAttachment>, key: String, requestHash: String): Map<String, Any?> {
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val assessment = studentAssessment(actorId, assessmentId, enforceDeadline = false) ?: throw AcademicNotFound()
        replay(actorId, "createSubmission", "/submissions", key, requestHash)?.let { return it }
        val existing = jdbc.query("SELECT id FROM core.submissions WHERE assessment_id=? AND user_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, assessmentId, actorId).firstOrNull()
        if (existing != null) {
            val row = authorized(actorId, role, sessionHash, existing) ?: throw AcademicNotFound()
            val result = dto(row)
            idempotency.save(actorId, "createSubmission", "/submissions", key, requestHash, result)
            return result
        }
        if (!beforeDeadline(assessment["deadline"] as Timestamp?, assessment["allowLate"] as Boolean)) throw AcademicNotFound()
        validateStagedFiles(actorId, assessmentId, attachments)
        val now = Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))
        val id = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.submissions(id,revision,created_at,updated_at,updated_by,assessment_id,user_id,attachments)
            VALUES(?,1,?,?,?,?,?,?::jsonb)""", id, now, now, actorId, assessmentId, actorId, attachmentJson(attachments))
        audit(actorId, "academic.submission_created", id)
        val result = authorized(actorId, role, sessionHash, id)?.let(::dto) ?: throw AcademicNotFound()
        idempotency.save(actorId, "createSubmission", "/submissions", key, requestHash, result)
        return result
    }

    @Transactional(readOnly = true)
    override fun submissions(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): SubmissionPage {
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        if (role == "student" && studentId != null && studentId != actorId) throw AcademicNotFound()
        val clauses = mutableListOf(accessClause(role))
        val args = mutableListOf<Any>()
        when (role) { "student", "teacher" -> args += actorId; "admin" -> Unit; else -> throw AcademicForbidden() }
        assessmentId?.let { clauses += "x.assessment_id=?"; args += it }
        if (role != "student" && studentId != null) { clauses += "x.user_id=?"; args += studentId }
        val where = clauses.joinToString(" AND ")
        val from = "core.submissions x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id JOIN core.users u ON u.id=x.user_id"
        val total = jdbc.queryForObject("SELECT count(*) FROM $from WHERE $where", Long::class.javaObjectType, *args.toTypedArray()) ?: 0L
        val order = if (sort == "-id") "x.id DESC" else "x.id"
        val rows = jdbc.query("SELECT x.*,a.max_grade,u.name student_name FROM $from WHERE $where ORDER BY $order LIMIT ? OFFSET ?", { rs, _ -> rs.submissionRow() + ("role" to role) }, *(args + listOf(pageSize, (page - 1) * pageSize)).toTypedArray())
        return SubmissionPage(rows.map(::dto), page, pageSize, total)
    }

    @Transactional(readOnly = true)
    override fun submission(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        return authorized(actorId, role, sessionHash, id)?.let(::dto)
    }

    @Transactional
    override fun update(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, attachments: List<SubmissionAttachment>?): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorized(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        if (current["submittedAt"] != null || current["revision"] != expectedRevision) throw AcademicConflict(current["revision"] as Int)
        val assessmentId = current["assessmentId"] as String
        if (studentAssessment(actorId, assessmentId) == null) throw AcademicNotFound()
        attachments?.let { validateStagedFiles(actorId, assessmentId, it) }
        val selectedJson = attachments?.let(::attachmentJson) ?: mapper.writeValueAsString(current["attachments"])
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.submissions SET attachments=?::jsonb,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", selectedJson, now, actorId, id, expectedRevision) != 1) throw AcademicConflict(current["revision"] as Int)
        audit(actorId, "academic.submission_updated", id)
        return authorized(actorId, role, sessionHash, id)?.let(::dto)
    }

    @Transactional
    override fun submit(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, key: String, requestHash: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorized(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        replay(actorId, "submitSubmission", "/submissions/$id/submit", key, requestHash)?.let { return it }
        if (current["submittedAt"] != null) {
            val result = dto(current)
            idempotency.save(actorId, "submitSubmission", "/submissions/$id/submit", key, requestHash, result)
            return result
        }
        if (current["revision"] != expectedRevision) throw AcademicConflict(current["revision"] as Int)
        val assessmentId = current["assessmentId"] as String
        val assessment = studentAssessment(actorId, assessmentId, enforceDeadline = false) ?: throw AcademicNotFound()
        if (!beforeDeadline(assessment["deadline"] as Timestamp?, assessment["allowLate"] as Boolean)) throw AcademicConflict(current["revision"] as Int)
        @Suppress("UNCHECKED_CAST")
        val attachments = current["attachments"] as List<Map<String, Any?>>
        if (attachments.isEmpty()) throw AcademicConflict(current["revision"] as Int)
        val fileIds = attachments.filter { it["kind"] == "file" }.map { it["fileId"] as String }.sorted()
        val lockedFileExpiries = if (fileIds.isNotEmpty()) {
            val placeholders = fileIds.joinToString(",") { "?" }
            jdbc.query("SELECT id,expires_at FROM core.files WHERE id IN ($placeholders) AND owner_id=? AND purpose='work' AND parent_id=? AND status='staged' ORDER BY id FOR UPDATE", { rs, _ -> rs.getString("id") to rs.getTimestamp("expires_at")?.toInstant() }, *(fileIds + listOf(actorId, assessmentId)).toTypedArray()).mapNotNull { (fileId, expiresAt) -> expiresAt?.let { fileId to it } }.toMap()
        } else emptyMap()
        val now = Timestamp.from(clock.instant())
        if (!beforeDeadline(assessment["deadline"] as Timestamp?, assessment["allowLate"] as Boolean, now.toInstant())) throw AcademicConflict(current["revision"] as Int)
        if (lockedFileExpiries.size != fileIds.size || fileIds.any { fileId -> lockedFileExpiries[fileId]?.isAfter(now.toInstant()) != true }) throw AcademicConflict(current["revision"] as Int)
        if (fileIds.isNotEmpty()) {
            val placeholders = fileIds.joinToString(",") { "?" }
            jdbc.update("UPDATE core.files SET status='attached',expires_at=NULL WHERE id IN ($placeholders) AND owner_id=? AND purpose='work' AND parent_id=? AND status='staged'", *(fileIds + listOf(actorId, assessmentId)).toTypedArray())
        }
        val receiptId = UUID.randomUUID().toString()
        if (jdbc.update("UPDATE core.submissions SET submitted_at=?,receipt_id=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", now, receiptId, now, actorId, id, expectedRevision) != 1) throw AcademicConflict(current["revision"] as Int)
        audit(actorId, "academic.submission_submitted", id)
        val result = authorized(actorId, role, sessionHash, id)?.let(::dto) ?: return null
        idempotency.save(actorId, "submitSubmission", "/submissions/$id/submit", key, requestHash, result)
        return result
    }

    @Transactional
    override fun saveFeedback(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, comment: String, grade: BigDecimal?): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash, "admin", "teacher")
        val current = feedbackWriteRow(actorId, role, sessionHash, id) ?: return null
        if (current["submittedAt"] == null) throw AcademicNotFound()
        if (current["revision"] != expectedRevision) throw AcademicConflict(current["revision"] as Int)
        val maxGrade = current["maxGrade"] as BigDecimal?
        if (grade != null && (maxGrade == null || grade > maxGrade)) throw InvalidSubmissionFeedback()
        val now = Timestamp.from(clock.instant()).toInstant().toString()
        val draft = mapOf("comment" to comment, "grade" to grade, "author" to mapOf("id" to actorId, "name" to current["actorName"]), "createdAt" to now)
        val updated = jdbc.update("""UPDATE core.submissions SET feedback_draft=?::jsonb,feedback_updated=(feedback_published IS NOT NULL),revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NOT NULL""", mapper.writeValueAsString(draft), Timestamp.from(clock.instant()), actorId, id, expectedRevision)
        if (updated != 1) throw AcademicConflict(current["revision"] as Int)
        audit(actorId, "academic.feedback_saved", id)
        return authorized(actorId, role, sessionHash, id)?.let(::dto)
    }

    @Transactional
    override fun publishFeedback(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, key: String, requestHash: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash, "admin", "teacher")
        val current = feedbackWriteRow(actorId, role, sessionHash, id) ?: return null
        replay(actorId, "publishSubmissionFeedback", "/submissions/$id/publish-feedback", key, requestHash)?.let { return it }
        if (current["submittedAt"] == null) throw AcademicNotFound()
        if (current["revision"] != expectedRevision) throw AcademicConflict(current["revision"] as Int)
        @Suppress("UNCHECKED_CAST")
        val draft = current["feedbackDraft"] as? Map<String, Any?> ?: throw AcademicConflict(current["revision"] as Int)
        if ((draft["comment"] as String).isBlank() && draft["grade"] == null) throw AcademicConflict(current["revision"] as Int)
        @Suppress("UNCHECKED_CAST")
        val history = current["feedbackHistory"] as List<Map<String, Any?>>
        val now = Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))
        val published = draft + ("author" to mapOf("id" to actorId, "name" to current["actorName"])) + ("createdAt" to now.toInstant().toString())
        jdbc.update("""UPDATE core.submissions SET feedback_draft=NULL,feedback_published=?::jsonb,feedback_history=?::jsonb,feedback_updated=false,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?""", mapper.writeValueAsString(published), mapper.writeValueAsString(history + published), now, actorId, id, expectedRevision)
        audit(actorId, "academic.feedback_published", id)
        val result = authorized(actorId, role, sessionHash, id)?.let(::dto) ?: return null
        idempotency.save(actorId, "publishSubmissionFeedback", "/submissions/$id/publish-feedback", key, requestHash, result)
        return result
    }

    private fun studentAssessment(actorId: String, id: String, enforceDeadline: Boolean = true): Map<String, Any?>? = jdbc.query("""SELECT a.deadline,a.allow_late FROM core.assessments a JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
        WHERE a.id=? AND a.kind='work' AND a.status='published' AND a.published_at<=? AND m.withdrawn_at IS NULL AND c.active
          AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id) AND (?=false OR a.deadline IS NULL OR a.allow_late OR ?<a.deadline) AND (e.enrolled_at IS NULL OR a.deadline IS NULL OR e.enrolled_at<=a.deadline))
        FOR SHARE OF a""", { rs, _ -> mapOf("deadline" to rs.getTimestamp("deadline"), "allowLate" to rs.getBoolean("allow_late")) }, id, Timestamp.from(clock.instant()), actorId, enforceDeadline, Timestamp.from(clock.instant())).firstOrNull()

    private fun authorized(actorId: String, role: String, sessionHash: String, id: String, forUpdate: Boolean = false): Map<String, Any?>? {
        val actorName = jdbc.query("SELECT name FROM core.users WHERE id=?", { rs, _ -> rs.getString(1) }, actorId).firstOrNull()
        val clause = accessClause(role)
        val args = listOf(id) + when (role) { "student", "teacher" -> listOf(actorId); "admin" -> emptyList(); else -> return null }
        return jdbc.query("SELECT x.*,a.max_grade,u.name student_name FROM core.submissions x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id JOIN core.users u ON u.id=x.user_id WHERE x.id=? AND $clause${if (forUpdate) " FOR UPDATE OF x" else ""}", { rs, _ -> rs.submissionRow() + ("actorName" to actorName) + ("role" to role) }, *args.toTypedArray()).firstOrNull()
    }

    private fun feedbackWriteRow(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        val scope = jdbc.query("SELECT s.id AS subject_id,c.id AS class_id FROM core.submissions x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id WHERE x.id=? AND m.withdrawn_at IS NULL", { rs, _ -> rs.getString("subject_id") to rs.getString("class_id") }, id).firstOrNull() ?: return null
        val active = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, scope.second).firstOrNull() ?: return null
        if (role == "teacher") jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, actorId)
        requireSession(actorId, sessionHash, "admin", "teacher")
        if (!active) throw AcademicConflict()
        if (role == "teacher" && !canWriteSubject(scope.first, actorId)) throw AcademicNotFound()
        return authorized(actorId, role, sessionHash, id, forUpdate = true)
    }

    private fun accessClause(role: String) = when (role) {
        "student" -> "x.user_id=? AND a.status='published' AND m.withdrawn_at IS NULL AND c.active AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users su ON su.id=e.student_id WHERE e.class_id=s.class_id AND e.student_id=x.user_id AND e.status='active' AND su.active AND su.email_confirmed_at IS NOT NULL AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id) AND (e.enrolled_at IS NULL OR a.deadline IS NULL OR e.enrolled_at<=a.deadline))"
        "teacher" -> "x.submitted_at IS NOT NULL AND EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users tu ON tu.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND tu.active AND tu.role='teacher')"
        "admin" -> "x.submitted_at IS NOT NULL"
        else -> "false"
    }

    private fun validateStagedFiles(actorId: String, assessmentId: String, attachments: List<SubmissionAttachment>) {
        val ids = attachments.filter { it.kind == "file" }.map { it.fileId!! }.sorted()
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        val rows = jdbc.query("SELECT id,name FROM core.files WHERE id IN ($placeholders) AND owner_id=? AND purpose='work' AND parent_id=? AND status='staged' AND expires_at>?", { rs, _ -> rs.getString("id") to rs.getString("name") }, *(ids + listOf(actorId, assessmentId, Timestamp.from(clock.instant()))).toTypedArray()).toMap()
        if (rows.keys != ids.toSet() || attachments.any { it.kind == "file" && rows[it.fileId]?.let { name -> name != it.name } != false }) throw AcademicConflict()
    }

    private fun dto(row: Map<String, Any?>): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>("id" to row["id"], "revision" to row["revision"], "createdAt" to row["createdAt"], "updatedAt" to row["updatedAt"], "updatedBy" to row["updatedBy"], "assessmentId" to row["assessmentId"], "userId" to row["userId"], "attachments" to row["attachments"], "submittedAt" to row["submittedAt"], "receipt" to (row["receiptId"] as String?)?.let { mapOf("id" to it, "resourceId" to row["id"], "submittedAt" to row["submittedAt"]) }, "feedbackPublished" to row["feedbackPublished"], "feedbackUpdated" to row["feedbackUpdated"])
        if (row["role"] != "student") {
            base["studentName"] = row["studentName"]
            base["feedbackDraft"] = row["feedbackDraft"]
            base["feedbackHistory"] = row["feedbackHistory"]
        }
        return base
    }

    private fun ResultSet.submissionRow(): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val attachments = mapper.readValue(getString("attachments"), List::class.java) as List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val history = mapper.readValue(getString("feedback_history"), List::class.java) as List<Map<String, Any?>>
        fun json(name: String): Map<String, Any?>? = getString(name)?.let { mapper.readValue(it, Map::class.java) as Map<String, Any?> }
        return mapOf("id" to getString("id"), "revision" to getInt("revision"), "createdAt" to getTimestamp("created_at").toInstant().toString(), "updatedAt" to getTimestamp("updated_at").toInstant().toString(), "updatedBy" to getString("updated_by"), "assessmentId" to getString("assessment_id"), "userId" to getString("user_id"), "attachments" to attachments, "submittedAt" to getTimestamp("submitted_at")?.toInstant()?.toString(), "receiptId" to getString("receipt_id"), "feedbackDraft" to json("feedback_draft"), "feedbackPublished" to json("feedback_published"), "feedbackUpdated" to getBoolean("feedback_updated"), "feedbackHistory" to history, "studentName" to getString("student_name"), "maxGrade" to getBigDecimal("max_grade"))
    }

    private fun attachmentJson(attachments: List<SubmissionAttachment>) = mapper.writeValueAsString(attachments.map { if (it.kind == "file") mapOf("kind" to it.kind, "fileId" to it.fileId, "name" to it.name) else mapOf("kind" to it.kind, "name" to it.name, "url" to it.url) })
    private fun beforeDeadline(deadline: Timestamp?, allowLate: Boolean, now: Instant = clock.instant()) = allowLate || deadline == null || now.atZone(brasilia).isBefore(deadline.toInstant().atZone(brasilia))
    private fun canWriteSubject(subjectId: String, actorId: String) = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id WHERE s.id=? AND c.active AND EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND u.active AND u.role='teacher'))", Boolean::class.java, subjectId, actorId) == true
    private fun requireSession(actorId: String, hash: String, vararg roles: String) {
        val accepted = roles.toList().ifEmpty { listOf("admin", "teacher", "student") }
        val placeholders = accepted.joinToString(",") { "?" }
        if (jdbc.query("SELECT 1 FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getInt(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).isEmpty()) throw AcademicForbidden()
    }
    private fun replay(actorId: String, operation: String, target: String, key: String, hash: String): Map<String, Any?>? = try { idempotency.find(actorId, operation, target, key, hash) } catch (_: IdempotencyKeyReuse) { throw br.com.ajadigital.core.application.academic.AcademicIdempotencyConflict() }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun audit(actorId: String, type: String, targetId: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), type, targetId, actorId) }

    private companion object { val brasilia: ZoneId = ZoneId.of("America/Sao_Paulo") }
}
