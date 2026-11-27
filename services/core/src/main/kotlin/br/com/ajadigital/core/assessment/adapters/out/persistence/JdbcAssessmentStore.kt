package br.com.ajadigital.core.assessment.adapters.out.persistence

import br.com.ajadigital.core.assessment.domain.models.AssessmentKind
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentRecord

import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentRecord

import br.com.ajadigital.core.assessment.application.models.PublishAssessmentCommand

import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery

import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery




import br.com.ajadigital.core.assessment.adapters.serialization.AssessmentRepresentation
import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.AssessmentResults
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionContent
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionView
import br.com.ajadigital.core.assessment.domain.models.AssessmentRecord
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.assessment.application.models.AssessmentReceiptKey
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Conflict
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.IdempotencyConflict
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Forbidden
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.NotFound
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.application.contracts.DashboardAssessment
import br.com.ajadigital.core.assessment.application.contracts.ListDashboardAssessmentsQuery
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.models.AssessmentState
import br.com.ajadigital.core.assessment.domain.policies.AssessmentEditPolicy
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentWriteOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcAssessmentStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val clock: Clock, private val idempotency: EncodedIdempotencyStoreOutputPort) : AssessmentReadOutputPort, AssessmentWriteOutputPort {
    @Transactional
    override fun authorizeWrite(actor: AuthenticatedActor, moduleId: String) {
        lockAuthInvariant()
        if (lockModuleForWrite(moduleId, actor) == null) throw NotFound()
    }

    @Transactional
    override fun create(request: CreateAssessmentRecord): AssessmentDetails.Staff {
        val actor = request.actor
        val actorId = actor.id
        val draft = request.draft
        lockAuthInvariant()
        lockModuleForWrite(draft.moduleId, actor) ?: throw NotFound()
        val command = AssessmentReceiptKey.create(actorId, request.key, request.requestHash)
        replay(command)?.let { return it }
        val now = Timestamp.from(clock.instant())
        val id = UUID.randomUUID().toString()
        val order = jdbc.queryForObject("SELECT COALESCE(max(order_index)+1,0) FROM core.assessments WHERE module_id=?", Int::class.java, draft.moduleId) ?: 0
        jdbc.update("""INSERT INTO core.assessments(id,module_id,kind,status,revision,published_at,deadline,allow_late,created_at,updated_at,updated_by,title,description,max_grade,required,order_index)
            VALUES(?,?,?,'draft',1,NULL,?,?,?,?,?,?,?,?,?,?)""", id, draft.moduleId, draft.kind.value, draft.deadline?.let(Timestamp::from), draft.allowLate, now, now, actorId, draft.title, draft.description, draft.maxGrade, draft.required, order)
        writeQuestions(id, request.questions)
        audit(actorId, "academic.assessment_created", id)
        return refreshAndSave(command, GetAssessmentQuery(actor, id))
    }

    @Transactional
    override fun update(request: UpdateAssessmentRecord): AssessmentDetails.Staff? {
        val actor = request.actor
        val actorId = actor.id
        val id = request.id
        val expectedRevision = request.expectedRevision
        val draft = request.draft
        lockAuthInvariant()
        val priorModule = jdbc.query("SELECT module_id FROM core.assessments WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return null
        val moduleIds = listOf(priorModule, draft.moduleId).distinct().sorted()
        moduleIds.forEach { lockModuleForWrite(it, actor) ?: throw NotFound() }
        val current = jdbc.query("SELECT * FROM core.assessments WHERE id=? FOR UPDATE", { rs, _ -> rs.assessmentRecord() }, id).firstOrNull() ?: return null
        val command = AssessmentReceiptKey.update(actorId, id, request.key, request.requestHash)
        replay(command)?.let { return it }
        if (current.revision != expectedRevision || current.draft.moduleId != priorModule) return null
        val submitted = hasSubmissions(id)
        val desiredQuestions = request.questions?.let { retainExistingSnapshots(id, it) }
        val changedQuestions = desiredQuestions != null && AssessmentEditPolicy.questionsChanged(questionSignature(id), desiredQuestions)
        val prior = current.draft
        val next = AssessmentEditPolicy.edit(prior, draft, submitted, changedQuestions, request.confirmResetDrafts)
            ?: throw Conflict(expectedRevision)
        val now = Timestamp.from(clock.instant())
        if (desiredQuestions != null && changedQuestions) {
            resetPendingAttempts(id)
            jdbc.update("DELETE FROM core.assessment_questions WHERE assessment_id=?", id)
            deleteUnusedAssessmentImages(id, desiredQuestions)
            writeQuestions(id, desiredQuestions)
        }
        if (jdbc.update("""UPDATE core.assessments SET module_id=?,kind=?,deadline=?,allow_late=?,title=?,description=?,max_grade=?,required=?,
            revision=revision+1,updated_at=?,updated_by=?,questions_updated_at=CASE WHEN ? THEN ? ELSE questions_updated_at END
            WHERE id=? AND revision=?""", next.moduleId, next.kind.value, next.deadline?.let(Timestamp::from), next.allowLate, next.title, next.description, next.maxGrade, next.required,
            now, actorId, changedQuestions, now, id, expectedRevision) != 1) return null
        if (prior.moduleId != next.moduleId) updatePublicationSubject(id, next.moduleId)
        audit(actorId, "academic.assessment_updated", id)
        return refreshAndSave(command, GetAssessmentQuery(actor, id))
    }

    @Transactional
    override fun publish(request: PublishAssessmentCommand): AssessmentDetails.Staff? {
        val (actor, id, key, expectedRevision, requestHash) = request
        val actorId = actor.id
        lockAuthInvariant()
        val moduleId = jdbc.query("SELECT module_id FROM core.assessments WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return null
        val module = lockModuleForWrite(moduleId, actor) ?: return null
        val row = jdbc.query("SELECT * FROM core.assessments WHERE id=? FOR UPDATE", { rs, _ -> rs.assessmentRecord() }, id).firstOrNull() ?: return null
        val command = AssessmentReceiptKey.publish(actorId, id, key, requestHash)
        replay(command)?.let { return it }
        if (row.revision != expectedRevision || row.draft.moduleId != moduleId) return null
        if (row.state != AssessmentState.Draft) throw Conflict(expectedRevision)
        val questionCount = questionSignature(id).size
        if (!AssessmentEditPolicy.canPublish(row.state, hasSubmissions(id), row.draft.kind, questionCount)) throw Conflict(expectedRevision)
        val now = Timestamp.from(clock.instant())
        jdbc.update("UPDATE core.assessments SET status='published',published_at=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", now, now, actorId, id, expectedRevision)
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?) ON CONFLICT DO NOTHING", module.first, "assessment:$id")
        audit(actorId, "academic.assessment_published", id)
        return refreshAndSave(command, GetAssessmentQuery(actor, id))
    }

    override fun assessment(request: GetAssessmentQuery): AssessmentDetails? {
        val (actor, id) = request
        val actorId = actor.id
        val role = requireSession(actorId, actor.sessionValidationReference)
        val where = accessPredicate(role)
        val row = jdbc.query("""SELECT a.*,m.subject_id,s.class_id,c.active AS class_active,
            (SELECT count(*)::integer FROM core.assessment_questions aq WHERE aq.assessment_id=a.id) AS question_count,
            EXISTS(SELECT 1 FROM core.enrollments e WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id)) AS assigned
            FROM core.assessments a JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
            WHERE a.id=? AND $where""", { rs, _ -> rs.readRow() },
            *(listOf(actorId, id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray(),
        ).firstOrNull() ?: return null
        return if (role == "student") row.student() else AssessmentDetails.Staff(
            row.record, questionRows(listOf(id))[id].orEmpty(), hasSubmissions(id),
        )
    }

    override fun assessments(request: ListAssessmentsQuery): AssessmentResults {
        val (actor, page, pageSize, sort, subjectId, moduleId, kind, status) = request
        val loaded = assessmentPage(actor.id, actor.sessionValidationReference, page, pageSize, sort, subjectId, moduleId, kind, status)
        return AssessmentResults(loaded.details, page, pageSize, loaded.total)
    }

    override fun dashboardAssessments(query: ListDashboardAssessmentsQuery): List<DashboardAssessment> {
        val (actor, pageSize) = query
        val loaded = assessmentPage(actor.id, actor.sessionValidationReference, 1, pageSize, "id", null, null, null, "published")
        return loaded.details.zip(loaded.rows).map { (details, row) ->
            val record = row.record
            val draft = record.draft
            DashboardAssessment(
                record.id, record.revision, record.createdAt, record.updatedAt, record.updatedBy,
                draft.moduleId, draft.title, draft.description, draft.kind.value, draft.maxGrade,
                draft.deadline, draft.required, draft.allowLate, checkNotNull(record.state.publishedAt),
                row.questionCount, (details as? AssessmentDetails.Student)?.assigned,
            )
        }
    }

    private fun assessmentPage(
        actorId: String, sessionHash: String, page: Int, pageSize: Int, sort: String,
        subjectId: String?, moduleId: String?, kind: String?, status: String?,
    ): LoadedPage {
        val role = requireSession(actorId, sessionHash)
        val clauses = mutableListOf(accessPredicate(role))
        val args = mutableListOf<Any>()
        if (role == "teacher" || role == "student") args += actorId
        if (subjectId != null) { clauses += "s.id=?"; args += subjectId }
        if (moduleId != null) { clauses += "m.id=?"; args += moduleId }
        if (kind != null) { clauses += "a.kind=?"; args += kind }
        if (status != null) { clauses += "a.status=?"; args += status }
        val where = clauses.joinToString(" AND ")
        val joins = "FROM core.assessments a JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id"
        val count = jdbc.queryForObject("SELECT count(*) $joins WHERE $where", Long::class.javaObjectType, *args.toTypedArray()) ?: 0L
        val order = if (sort == "-id") "a.id DESC" else "a.id"
        val rows = jdbc.query("""SELECT a.*,m.subject_id,s.class_id,c.active AS class_active,
            (SELECT count(*)::integer FROM core.assessment_questions aq WHERE aq.assessment_id=a.id) AS question_count,
            EXISTS(SELECT 1 FROM core.enrollments e WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id)) AS assigned
            $joins WHERE $where ORDER BY $order LIMIT ? OFFSET ?""", { rs, _ -> rs.readRow() },
            *(listOf(actorId) + args + listOf(pageSize, (page - 1L) * pageSize)).toTypedArray(),
        )
        val items = if (role == "student") rows.map(ReadRow::student) else {
            val ids = rows.map { it.record.id }
            val questions = questionRows(ids)
            val submissions = hasSubmissions(ids)
            rows.map { row -> AssessmentDetails.Staff(row.record, questions[row.record.id].orEmpty(), row.record.id in submissions) }
        }
        return LoadedPage(rows, items, count)
    }

    private data class LoadedPage(val rows: List<ReadRow>, val details: List<AssessmentDetails>, val total: Long)

    private data class ReadRow(val record: AssessmentRecord, val assigned: Boolean, val questionCount: Int) {
        fun student() = AssessmentDetails.Student(record, questionCount, assigned)
    }

    private fun ResultSet.readRow() = ReadRow(assessmentRecord(), getBoolean("assigned"), getInt("question_count"))

    @Transactional(readOnly = true)
    override fun pendingStudentCount(actor: AuthenticatedActor): Int {
        val actorId = actor.id
        val sessionHash = actor.sessionValidationReference
        requireSession(actorId, sessionHash, "student")
        val where = accessPredicate("student")
        return jdbc.queryForObject("""SELECT count(*) FROM core.assessments a
            JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
            WHERE $where AND ((a.kind='quiz' AND NOT EXISTS(
                SELECT 1 FROM core.attempts x WHERE x.assessment_id=a.id AND x.user_id=? AND x.submitted_at IS NOT NULL
            )) OR (a.kind='work' AND NOT EXISTS(
                SELECT 1 FROM core.submissions x WHERE x.assessment_id=a.id AND x.user_id=? AND x.submitted_at IS NOT NULL
            )))""", Int::class.java, actorId, actorId, actorId) ?: 0
    }

    private fun questionRows(ids: List<String>): Map<String, List<AssessmentQuestionView>> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        return jdbc.query("SELECT assessment_id,question_id,source,source_revision,content_hash,snapshot::text FROM core.assessment_questions WHERE assessment_id IN ($placeholders) ORDER BY assessment_id,position", { rs, _ ->
            val snapshot = AssessmentRepresentation.readQuestion(mapper.readTree(rs.getString("snapshot")))
            rs.getString("assessment_id") to AssessmentQuestionView(rs.getString("question_id"), rs.getString("source"), snapshot)
        }, *ids.toTypedArray()).groupBy({ it.first }, { it.second })
    }

    private fun writeQuestions(id: String, questions: List<AssessmentQuestion>) {
        questions.forEachIndexed { position, question ->
            val snapshot = snapshotImage(id, question)
            jdbc.update("""INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot)
                VALUES(?,?,?,?,?,?,?::jsonb)""", id, question.id, position, question.source, question.sourceRevision, question.contentHash, mapper.writeValueAsString(AssessmentRepresentation.question(question.id, question.source, snapshot)))
        }
    }

    private fun snapshotImage(assessmentId: String, question: AssessmentQuestion): AssessmentQuestionContent {
        val snapshot = question.snapshot ?: throw NotFound()
        if (question.source != "bank") return snapshot
        val image = snapshot.image ?: return snapshot
        val sourceId = assessmentImageId(image.src) ?: throw NotFound()
        val source = jdbc.query("SELECT purpose,parent_id,name,mime,size,sha256,content FROM core.files WHERE id=? AND status='attached'", { rs, _ ->
            listOf(rs.getString("purpose"), rs.getString("parent_id"), rs.getString("name"), rs.getString("mime"), rs.getLong("size"), rs.getString("sha256"), rs.getBytes("content"))
        }, sourceId).firstOrNull() ?: throw NotFound()
        if (source[0] == "assessment" && source[1] == assessmentId) return snapshot
        if (source[0] != "question") throw NotFound()
        val copiedId = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.files(id,purpose,parent_id,name,mime,size,sha256,status,content,created_at)
            VALUES(?,'assessment',?,?,?,?,?,'attached',?,?)""", copiedId, assessmentId, source[2], source[3], source[4], source[5], source[6], Timestamp.from(clock.instant()))
        return snapshot.withImage(image.copy(src = "/api/v1/files/$copiedId/content"))
    }

    private fun deleteUnusedAssessmentImages(assessmentId: String, questions: List<AssessmentQuestion>) {
        val keep = questions.mapNotNull { question -> question.snapshot?.image?.src }
            .mapNotNull(::assessmentImageId).toSet()
        jdbc.query("SELECT id FROM core.files WHERE purpose='assessment' AND parent_id=?", { rs, _ -> rs.getString(1) }, assessmentId)
            .filterNot { it in keep }
            .forEach { jdbc.update("DELETE FROM core.files WHERE id=?", it) }
    }

    private fun assessmentImageId(src: String?): String? = Regex("/api/v1/files/([A-Za-z0-9][A-Za-z0-9._:-]{0,127})/content").matchEntire(src ?: "")?.groupValues?.get(1)

    private fun retainExistingSnapshots(id: String, questions: List<AssessmentQuestion>): List<AssessmentQuestion> = questions.map { question ->
        if (question.snapshot != null) question else jdbc.query("SELECT source_revision,content_hash,snapshot::text FROM core.assessment_questions WHERE assessment_id=? AND question_id=? AND source='bank'", { rs, _ ->
            question.copy(
                snapshot = AssessmentRepresentation.readQuestion(mapper.readTree(rs.getString("snapshot"))),
                sourceRevision = rs.getInt("source_revision").takeUnless { rs.wasNull() },
                contentHash = rs.getString("content_hash"),
            )
        }, id, question.id).firstOrNull() ?: throw NotFound()
    }

    private fun questionSignature(id: String): List<AssessmentQuestionView> = jdbc.query("SELECT question_id,snapshot::text FROM core.assessment_questions WHERE assessment_id=? ORDER BY position", { rs, _ ->
        val node = mapper.readTree(rs.getString(2))
        AssessmentQuestionView(rs.getString(1), checkNotNull(node.required("origin").stringValue()), AssessmentRepresentation.readQuestion(node))
    }, id)
    private fun hasSubmissions(ids: List<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val tables = jdbc.queryForObject("SELECT to_regclass('core.attempts') IS NOT NULL AS attempts, to_regclass('core.submissions') IS NOT NULL AS submissions") { rs, _ -> rs.getBoolean("attempts") to rs.getBoolean("submissions") }
        val placeholders = ids.joinToString(",") { "?" }
        return buildSet {
            if (tables.first) addAll(jdbc.query("SELECT DISTINCT assessment_id FROM core.attempts WHERE assessment_id IN ($placeholders) AND submitted_at IS NOT NULL", { rs, _ -> rs.getString(1) }, *ids.toTypedArray()))
            if (tables.second) addAll(jdbc.query("SELECT DISTINCT assessment_id FROM core.submissions WHERE assessment_id IN ($placeholders) AND submitted_at IS NOT NULL", { rs, _ -> rs.getString(1) }, *ids.toTypedArray()))
        }
    }

    private fun hasSubmissions(id: String): Boolean = id in hasSubmissions(listOf(id))

    private fun resetPendingAttempts(id: String) {
        if (jdbc.queryForObject("SELECT to_regclass('core.attempts') IS NOT NULL", Boolean::class.java) != true) return
        jdbc.update("""DELETE FROM core.command_idempotency c USING core.attempts a
            WHERE a.assessment_id=? AND a.submitted_at IS NULL AND c.actor_id=a.user_id
              AND c.operation='startAttempt' AND c.target='/attempts'
              AND c.response_body::jsonb->>'assessmentId'=a.assessment_id""", id)
        jdbc.update("DELETE FROM core.attempts WHERE assessment_id=? AND submitted_at IS NULL", id)
    }

    private fun updatePublicationSubject(id: String, moduleId: String) {
        val subjectId = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleId) ?: throw NotFound()
        jdbc.update("DELETE FROM core.subject_publications WHERE publication_id=?", "assessment:$id")
        if (jdbc.queryForObject("SELECT status='published' FROM core.assessments WHERE id=?", Boolean::class.java, id) == true) jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "assessment:$id")
    }

    private fun lockModuleForWrite(moduleId: String, actor: AuthenticatedActor): Pair<String, Int>? {
        val actorId = actor.id
        val sessionHash = actor.sessionValidationReference
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val found = jdbc.query("SELECT s.class_id FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=? AND m.withdrawn_at IS NULL", { rs, _ -> rs.getString(1) }, moduleId).firstOrNull() ?: return null
        val activeClass = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, found).firstOrNull() ?: return null
        if (currentRole == "teacher") jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, actorId)
        val subjectId = jdbc.query("SELECT id FROM core.subjects WHERE class_id=? AND id=(SELECT subject_id FROM core.modules WHERE id=?) FOR UPDATE", { rs, _ -> rs.getString(1) }, found, moduleId).firstOrNull() ?: return null
        val module = jdbc.query("SELECT revision FROM core.modules WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> rs.getInt(1) }, moduleId).firstOrNull() ?: return null
        val allowed = activeClass && (currentRole == "admin" || canWriteSubject(subjectId, actorId))
        requireSession(actorId, sessionHash, "admin", "teacher")
        if (!allowed) throw Forbidden()
        return subjectId to module
    }

    private fun canWriteSubject(subjectId: String, actorId: String) = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id JOIN core.subjects s ON s.id=st.subject_id JOIN core.classes c ON c.id=s.class_id WHERE st.subject_id=? AND st.teacher_id=? AND u.active AND u.role='teacher' AND c.active)", Boolean::class.java, subjectId, actorId) == true
    private fun accessPredicate(role: String) = when (role) {
        "admin" -> "true"
        "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND u.active AND u.role='teacher')"
        "student" -> "a.status='published' AND m.withdrawn_at IS NULL AND c.active AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id))"
        else -> "false"
    }
    private fun requireSession(actorId: String, hash: String, vararg roles: String): String {
        val accepted = if (roles.isEmpty()) listOf("admin", "teacher", "student") else roles.toList()
        val placeholders = accepted.joinToString(",") { "?" }
        return jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).firstOrNull() ?: throw Forbidden()
    }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    override fun replay(request: AssessmentReceiptKey): AssessmentDetails.Staff? = try {
        idempotency.findEncoded(request.toStorageKey())?.let { AssessmentRepresentation.readStaff(mapper.readTree(it)) }
    } catch (_: IdempotencyKeyReuse) {
        throw IdempotencyConflict()
    }

    private fun refreshAndSave(command: AssessmentReceiptKey, query: GetAssessmentQuery): AssessmentDetails.Staff {
        val result = assessment(query) as? AssessmentDetails.Staff ?: throw NotFound()
        idempotency.saveEncoded(command.toStorageKey(), mapper.writeValueAsString(AssessmentRepresentation.response(result)))
        return result
    }

    private fun AssessmentReceiptKey.toStorageKey() =
        FindIdempotencyDto(actorId, operation, target, key, requestHash)

    private fun audit(actorId: String, eventType: String, id: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), eventType, id, actorId) }
    private fun ResultSet.assessmentRecord() = AssessmentRecord(
        getString("id"), getInt("revision"), getTimestamp("created_at").toInstant(),
        getTimestamp("updated_at").toInstant(), getString("updated_by"),
        AssessmentDraft(
            getString("module_id"), getString("title"), getString("description"), AssessmentKind.parse(getString("kind")),
            getBigDecimal("max_grade")?.toDouble(), getTimestamp("deadline")?.toInstant(),
            getBoolean("required"), getBoolean("allow_late"),
        ),
        AssessmentRepresentation.state(getString("status"), getTimestamp("published_at")?.toInstant()),
    )
}
