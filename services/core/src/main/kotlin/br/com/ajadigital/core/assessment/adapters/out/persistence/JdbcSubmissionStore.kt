package br.com.ajadigital.core.assessment.adapters.out.persistence

import br.com.ajadigital.core.assessment.application.models.SubmissionsListQuery

import br.com.ajadigital.core.assessment.application.models.PendingFeedbackCountQuery

import br.com.ajadigital.core.assessment.application.models.SubmissionGetQuery

import br.com.ajadigital.core.assessment.application.models.SubmissionUpdateRecord

import br.com.ajadigital.core.assessment.application.models.SubmissionSubmitRecord

import br.com.ajadigital.core.assessment.application.models.SubmissionSaveFeedbackRecord

import br.com.ajadigital.core.assessment.application.models.SubmissionPublishFeedbackRecord

import br.com.ajadigital.core.assessment.application.models.SubmissionCreateRecord

import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentConflict
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentForbidden
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.assessment.domain.exceptions.InvalidSubmissionFeedback
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import br.com.ajadigital.core.assessment.domain.models.SubmissionPage
import br.com.ajadigital.core.assessment.domain.models.SubmissionView
import br.com.ajadigital.core.assessment.domain.policies.DeadlinePolicy
import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Repository
class JdbcSubmissionStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
) : SubmissionStoreOutputPort {
    @Transactional
    override fun create(request: SubmissionCreateRecord): SubmissionView {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        val assessmentId = request.assessmentId
        val attachments = request.attachments
        val key = request.key
        val requestHash = request.requestHash
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val assessment = studentAssessment(actorId, assessmentId, enforceDeadline = false) ?: throw AssessmentNotFound()
        replay(actorId, "createSubmission", "/submissions", key, requestHash)?.let { return view(it, role) }
        val existing = jdbc.query("SELECT id FROM core.submissions WHERE assessment_id=? AND user_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, assessmentId, actorId).firstOrNull()
        if (existing != null) {
            val row = authorized(actorId, role, sessionHash, existing) ?: throw AssessmentNotFound()
            val result = dto(row)
            idempotency.saveEncoded(FindIdempotencyDto(actorId, "createSubmission", "/submissions", key, requestHash), mapper.writeValueAsString(result))
            return view(result, role)
        }
        if (!DeadlinePolicy.isBeforeDeadline((assessment["deadline"] as Timestamp?)?.toInstant(), assessment["allowLate"] as Boolean, clock.instant())) throw AssessmentNotFound()
        validateStagedFiles(actorId, assessmentId, attachments)
        val now = Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))
        val id = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.submissions(id,revision,created_at,updated_at,updated_by,assessment_id,user_id,attachments)
            VALUES(?,1,?,?,?,?,?,?::jsonb)""", id, now, now, actorId, assessmentId, actorId, attachmentJson(attachments))
        audit(actorId, "academic.submission_created", id)
        val result = authorized(actorId, role, sessionHash, id)?.let(::dto) ?: throw AssessmentNotFound()
        idempotency.saveEncoded(FindIdempotencyDto(actorId, "createSubmission", "/submissions", key, requestHash), mapper.writeValueAsString(result))
        return view(result, role)
    }

    @Transactional(readOnly = true)
    override fun submissions(request: SubmissionsListQuery): SubmissionPage {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        val page = request.page
        val pageSize = request.pageSize
        val sort = request.sort
        val assessmentId = request.assessmentId
        val studentId = request.studentId
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        if (role == "student" && studentId != null && studentId != actorId) throw AssessmentNotFound()
        val clauses = mutableListOf(accessClause(role))
        val args = mutableListOf<Any>()
        when (role) { "student", "teacher" -> args += actorId; "admin" -> Unit; else -> throw AssessmentForbidden() }
        assessmentId?.let { clauses += "x.assessment_id=?"; args += it }
        if (role != "student" && studentId != null) { clauses += "x.user_id=?"; args += studentId }
        val where = clauses.joinToString(" AND ")
        val from = "core.submissions x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id JOIN core.users u ON u.id=x.user_id"
        val total = jdbc.queryForObject("SELECT count(*) FROM $from WHERE $where", Long::class.javaObjectType, *args.toTypedArray()) ?: 0L
        val order = if (sort == "-id") "x.id DESC" else "x.id"
        val rows = jdbc.query("SELECT x.*,a.max_grade,u.name student_name FROM $from WHERE $where ORDER BY $order LIMIT ? OFFSET ?", { rs, _ -> rs.submissionRow() + ("role" to role) }, *(args + listOf(pageSize, (page - 1) * pageSize)).toTypedArray())
        return SubmissionPage(rows.map { view(dto(it), role) }, page, pageSize, total)
    }

    @Transactional(readOnly = true)
    override fun pendingFeedbackCount(request: PendingFeedbackCountQuery): Int {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        require(role in setOf("teacher", "admin"))
        lockAuthInvariant(); requireSession(actorId, sessionHash, role)
        val where = accessClause(role) + " AND x.feedback_published IS NULL"
        val args = if (role == "teacher") arrayOf(actorId) else emptyArray()
        val from = "core.submissions x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id"
        return jdbc.queryForObject("SELECT count(*) FROM $from WHERE $where", Int::class.java, *args) ?: 0
    }

    @Transactional(readOnly = true)
    override fun submission(request: SubmissionGetQuery): SubmissionView? {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        val id = request.id
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        return authorized(actorId, role, sessionHash, id)?.let { view(dto(it), role) }
    }

    @Transactional
    override fun update(request: SubmissionUpdateRecord): SubmissionView? {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        val id = request.id
        val expectedRevision = request.expectedRevision
        val attachments = request.attachments
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorized(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        if (current["submittedAt"] != null || current["revision"] != expectedRevision) throw AssessmentConflict(current["revision"] as Int)
        val assessmentId = current["assessmentId"] as String
        if (studentAssessment(actorId, assessmentId) == null) throw AssessmentNotFound()
        attachments?.let { validateStagedFiles(actorId, assessmentId, it) }
        val selectedJson = attachments?.let(::attachmentJson) ?: mapper.writeValueAsString(current["attachments"])
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.submissions SET attachments=?::jsonb,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", selectedJson, now, actorId, id, expectedRevision) != 1) throw AssessmentConflict(current["revision"] as Int)
        audit(actorId, "academic.submission_updated", id)
        return authorized(actorId, role, sessionHash, id)?.let { view(dto(it), role) }
    }

    @Transactional
    override fun submit(request: SubmissionSubmitRecord): SubmissionView? {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        val id = request.id
        val expectedRevision = request.expectedRevision
        val key = request.key
        val requestHash = request.requestHash
        lockAuthInvariant(); requireSession(actorId, sessionHash, "student")
        val current = authorized(actorId, role, sessionHash, id, forUpdate = true) ?: return null
        replay(actorId, "submitSubmission", "/submissions/$id/submit", key, requestHash)?.let { return view(it, role) }
        if (current["submittedAt"] != null) {
            val result = dto(current)
            idempotency.saveEncoded(FindIdempotencyDto(actorId, "submitSubmission", "/submissions/$id/submit", key, requestHash), mapper.writeValueAsString(result))
            return view(result, role)
        }
        if (current["revision"] != expectedRevision) throw AssessmentConflict(current["revision"] as Int)
        val assessmentId = current["assessmentId"] as String
        val assessment = studentAssessment(actorId, assessmentId, enforceDeadline = false) ?: throw AssessmentNotFound()
        if (!DeadlinePolicy.isBeforeDeadline((assessment["deadline"] as Timestamp?)?.toInstant(), assessment["allowLate"] as Boolean, clock.instant())) throw AssessmentConflict(current["revision"] as Int)
        @Suppress("UNCHECKED_CAST")
        val attachments = current["attachments"] as List<Map<String, Any?>>
        if (attachments.isEmpty()) throw AssessmentConflict(current["revision"] as Int)
        val fileIds = attachments.filter { it["kind"] == "file" }.map { it["fileId"] as String }.sorted()
        val lockedFileExpiries = if (fileIds.isNotEmpty()) {
            val placeholders = fileIds.joinToString(",") { "?" }
            jdbc.query("SELECT id,expires_at FROM core.files WHERE id IN ($placeholders) AND owner_id=? AND purpose='work' AND parent_id=? AND status='staged' ORDER BY id FOR UPDATE", { rs, _ -> rs.getString("id") to rs.getTimestamp("expires_at")?.toInstant() }, *(fileIds + listOf(actorId, assessmentId)).toTypedArray()).mapNotNull { (fileId, expiresAt) -> expiresAt?.let { fileId to it } }.toMap()
        } else emptyMap()
        val now = Timestamp.from(clock.instant())
        if (!DeadlinePolicy.isBeforeDeadline((assessment["deadline"] as Timestamp?)?.toInstant(), assessment["allowLate"] as Boolean, now.toInstant())) throw AssessmentConflict(current["revision"] as Int)
        if (!SubmissionPolicy.areStagedFilesValid(fileIds, lockedFileExpiries, now.toInstant())) throw AssessmentConflict(current["revision"] as Int)
        if (fileIds.isNotEmpty()) {
            val placeholders = fileIds.joinToString(",") { "?" }
            jdbc.update("UPDATE core.files SET status='attached',expires_at=NULL WHERE id IN ($placeholders) AND owner_id=? AND purpose='work' AND parent_id=? AND status='staged'", *(fileIds + listOf(actorId, assessmentId)).toTypedArray())
        }
        val receiptId = UUID.randomUUID().toString()
        if (jdbc.update("UPDATE core.submissions SET submitted_at=?,receipt_id=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NULL", now, receiptId, now, actorId, id, expectedRevision) != 1) throw AssessmentConflict(current["revision"] as Int)
        audit(actorId, "academic.submission_submitted", id)
        val result = authorized(actorId, role, sessionHash, id)?.let(::dto) ?: return null
        idempotency.saveEncoded(FindIdempotencyDto(actorId, "submitSubmission", "/submissions/$id/submit", key, requestHash), mapper.writeValueAsString(result))
        return view(result, role)
    }

    @Transactional
    override fun saveFeedback(request: SubmissionSaveFeedbackRecord): SubmissionView? {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        val id = request.id
        val expectedRevision = request.expectedRevision
        val comment = request.comment
        val grade = request.grade
        lockAuthInvariant(); requireSession(actorId, sessionHash, "admin", "teacher")
        val current = feedbackWriteRow(actorId, role, sessionHash, id) ?: return null
        if (current["submittedAt"] == null) throw AssessmentNotFound()
        if (current["revision"] != expectedRevision) throw AssessmentConflict(current["revision"] as Int)
        val maxGrade = current["maxGrade"] as BigDecimal?
        if (!SubmissionPolicy.isValidFeedbackGrade(grade, maxGrade)) throw InvalidSubmissionFeedback()
        val now = Timestamp.from(clock.instant()).toInstant().toString()
        val draft = mapOf("comment" to comment, "grade" to grade, "author" to mapOf("id" to actorId, "name" to current["actorName"]), "createdAt" to now)
        val updated = jdbc.update("""UPDATE core.submissions SET feedback_draft=?::jsonb,feedback_updated=(feedback_published IS NOT NULL),revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND submitted_at IS NOT NULL""", mapper.writeValueAsString(draft), Timestamp.from(clock.instant()), actorId, id, expectedRevision)
        if (updated != 1) throw AssessmentConflict(current["revision"] as Int)
        audit(actorId, "academic.feedback_saved", id)
        return authorized(actorId, role, sessionHash, id)?.let { view(dto(it), role) }
    }

    @Transactional
    override fun publishFeedback(request: SubmissionPublishFeedbackRecord): SubmissionView? {
        val actorId = request.actor.id
        val role = request.actor.role
        val sessionHash = request.actor.sessionValidationReference
        val id = request.id
        val expectedRevision = request.expectedRevision
        val key = request.key
        val requestHash = request.requestHash
        lockAuthInvariant(); requireSession(actorId, sessionHash, "admin", "teacher")
        val current = feedbackWriteRow(actorId, role, sessionHash, id) ?: return null
        replay(actorId, "publishSubmissionFeedback", "/submissions/$id/publish-feedback", key, requestHash)?.let { return view(it, role) }
        if (current["submittedAt"] == null) throw AssessmentNotFound()
        if (current["revision"] != expectedRevision) throw AssessmentConflict(current["revision"] as Int)
        @Suppress("UNCHECKED_CAST")
        val draft = current["feedbackDraft"] as? Map<String, Any?> ?: throw AssessmentConflict(current["revision"] as Int)
        if (!SubmissionPolicy.canPublishFeedback(draft["comment"] as String, (draft["grade"] as? Number)?.let { BigDecimal(it.toString()) })) throw AssessmentConflict(current["revision"] as Int)
        @Suppress("UNCHECKED_CAST")
        val history = current["feedbackHistory"] as List<Map<String, Any?>>
        val now = Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS))
        val published = draft + ("author" to mapOf("id" to actorId, "name" to current["actorName"])) + ("createdAt" to now.toInstant().toString())
        jdbc.update("""UPDATE core.submissions SET feedback_draft=NULL,feedback_published=?::jsonb,feedback_history=?::jsonb,feedback_updated=false,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?""", mapper.writeValueAsString(published), mapper.writeValueAsString(history + published), now, actorId, id, expectedRevision)
        audit(actorId, "academic.feedback_published", id)
        val result = authorized(actorId, role, sessionHash, id)?.let(::dto) ?: return null
        idempotency.saveEncoded(FindIdempotencyDto(actorId, "publishSubmissionFeedback", "/submissions/$id/publish-feedback", key, requestHash), mapper.writeValueAsString(result))
        return view(result, role)
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
        if (!active) throw AssessmentConflict()
        if (role == "teacher" && !canWriteSubject(scope.first, actorId)) throw AssessmentNotFound()
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
        if (rows.keys != ids.toSet() || attachments.any { it.kind == "file" && rows[it.fileId]?.let { name -> name != it.name } != false }) throw AssessmentConflict()
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

    private fun view(response: Map<String, Any?>, role: String): SubmissionView {
        @Suppress("UNCHECKED_CAST")
        val attachments = (response["attachments"] as List<Map<String, Any?>>).map {
            SubmissionView.Attachment(it["kind"] as String, it["name"] as String, it["fileId"] as String?, it["url"] as String?)
        }
        fun feedback(value: Any?): SubmissionView.Feedback? {
            @Suppress("UNCHECKED_CAST")
            val item = value as? Map<String, Any?> ?: return null
            @Suppress("UNCHECKED_CAST")
            val author = item["author"] as Map<String, Any?>
            val grade = when (val rawGrade = item["grade"]) {
                null -> null
                is Byte -> SubmissionView.Grade.Integer(rawGrade.toInt())
                is Short -> SubmissionView.Grade.Integer(rawGrade.toInt())
                is Int -> SubmissionView.Grade.Integer(rawGrade)
                is Long -> SubmissionView.Grade.LongInteger(rawGrade)
                is Float -> SubmissionView.Grade.Floating(rawGrade.toDouble())
                is Double -> SubmissionView.Grade.Floating(rawGrade)
                is BigDecimal -> SubmissionView.Grade.Decimal(rawGrade)
                is java.math.BigInteger -> SubmissionView.Grade.BigIntegerValue(rawGrade)
                else -> throw IllegalStateException("Stored feedback grade is not a JSON number")
            }
            return SubmissionView.Feedback(item["comment"] as String, grade, SubmissionView.Author(author["id"] as String, author["name"] as String), item["createdAt"] as String)
        }
        val submittedAt = response["submittedAt"] as String?
        val receipt = (response["receipt"] as? Map<*, *>)?.let {
            SubmissionView.Receipt(it["id"] as String, it["resourceId"] as String, it["submittedAt"] as String)
        }
        val id = response["id"] as String
        val revision = response["revision"] as Int
        val createdAt = response["createdAt"] as String
        val updatedAt = response["updatedAt"] as String
        val updatedBy = response["updatedBy"] as String
        val assessmentId = response["assessmentId"] as String
        val userId = response["userId"] as String
        if (role == "student") {
            if (submittedAt == null) return SubmissionView.StudentPending(
                id, revision, createdAt, updatedAt, updatedBy, assessmentId, userId, attachments,
            )
            return SubmissionView.StudentSubmitted(
                id, revision, createdAt, updatedAt, updatedBy, assessmentId, userId, attachments, submittedAt, requireNotNull(receipt),
                feedback(response["feedbackPublished"]), response["feedbackUpdated"] as Boolean,
            )
        }
        @Suppress("UNCHECKED_CAST")
        val history = (response["feedbackHistory"] as List<Map<String, Any?>>).mapNotNull(::feedback)
        return SubmissionView.StaffSubmitted(
            id, revision, createdAt, updatedAt, updatedBy, assessmentId, userId, attachments, requireNotNull(submittedAt), requireNotNull(receipt),
            feedback(response["feedbackPublished"]), response["feedbackUpdated"] as Boolean,
            response["studentName"] as String, feedback(response["feedbackDraft"]), history,
        )
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
    private fun canWriteSubject(subjectId: String, actorId: String) = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id WHERE s.id=? AND c.active AND EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND u.active AND u.role='teacher'))", Boolean::class.java, subjectId, actorId) == true
    private fun requireSession(actorId: String, hash: String, vararg roles: String) {
        val accepted = roles.toList().ifEmpty { listOf("admin", "teacher", "student") }
        val placeholders = accepted.joinToString(",") { "?" }
        if (jdbc.query("SELECT 1 FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getInt(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).isEmpty()) throw AssessmentForbidden()
    }
    @Suppress("UNCHECKED_CAST")
    private fun readReceipt(value: String): Map<String, Any?> = mapper.readValue(value, Map::class.java) as Map<String, Any?>
    private fun replay(actorId: String, operation: String, target: String, key: String, hash: String): Map<String, Any?>? = try { idempotency.findEncoded(FindIdempotencyDto(actorId, operation, target, key, hash))?.let(::readReceipt) } catch (_: IdempotencyKeyReuse) { throw br.com.ajadigital.core.assessment.domain.exceptions.AssessmentIdempotencyConflict() }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun audit(actorId: String, type: String, targetId: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), type, targetId, actorId) }
}
