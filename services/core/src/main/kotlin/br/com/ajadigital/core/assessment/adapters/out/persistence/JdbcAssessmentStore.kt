package br.com.ajadigital.core.assessment.adapters.out.persistence

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentPage
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.policies.AssessmentEditPolicy
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcAssessmentStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val clock: Clock, private val idempotency: IdempotencyStore) : AssessmentStore {
    @Transactional
    override fun authorizeWrite(actorId: String, role: String, sessionHash: String, moduleId: String) {
        lockAuthInvariant()
        if (lockModuleForWrite(moduleId, actorId, sessionHash, role) == null) throw AcademicNotFound()
    }

    @Transactional
    override fun create(actorId: String, role: String, sessionHash: String, key: String, requestHash: String, draft: AssessmentDraft, questions: List<AssessmentQuestion>): Map<String, Any?> {
        lockAuthInvariant()
        val module = lockModuleForWrite(draft.moduleId, actorId, sessionHash, role) ?: throw AcademicNotFound()
        replay(actorId, "createAssessment", "/assessments", key, requestHash)?.let { return it }
        val now = Timestamp.from(clock.instant())
        val id = UUID.randomUUID().toString()
        val order = jdbc.queryForObject("SELECT COALESCE(max(order_index)+1,0) FROM core.assessments WHERE module_id=?", Int::class.java, draft.moduleId) ?: 0
        jdbc.update("""INSERT INTO core.assessments(id,module_id,kind,status,revision,published_at,deadline,allow_late,created_at,updated_at,updated_by,title,description,max_grade,required,order_index)
            VALUES(?,?,?,'draft',1,NULL,?,?,?,?,?,?,?,?,?,?)""", id, draft.moduleId, draft.kind, draft.deadline?.let(Timestamp::from), draft.allowLate, now, now, actorId, draft.title, draft.description, draft.maxGrade, draft.required, order)
        writeQuestions(id, questions)
        audit(actorId, "academic.assessment_created", id)
        val result = assessment(actorId, role, sessionHash, id) ?: throw AcademicNotFound()
        idempotency.save(actorId, "createAssessment", "/assessments", key, requestHash, result)
        return result
    }

    @Transactional
    override fun update(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int, draft: AssessmentDraft?, questions: List<AssessmentQuestion>?, confirmResetDrafts: Boolean): Map<String, Any?>? {
        lockAuthInvariant()
        val priorModule = jdbc.query("SELECT module_id FROM core.assessments WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return null
        val moduleIds = (listOf(priorModule) + listOfNotNull(draft?.moduleId)).distinct().sorted()
        moduleIds.forEach { lockModuleForWrite(it, actorId, sessionHash, role) ?: throw AcademicNotFound() }
        val current = jdbc.query("SELECT * FROM core.assessments WHERE id=? FOR UPDATE", { rs, _ -> rs.assessmentBase() }, id).firstOrNull() ?: return null
        replay(actorId, "updateAssessment", "/assessments/$id", key, requestHash)?.let { return it }
        if (current["revision"] != expectedRevision || current["moduleId"] != priorModule) return null
        val submitted = hasSubmissions(id)
        val desiredQuestions = questions?.let { retainExistingSnapshots(id, it) }
        val changedQuestions = desiredQuestions != null && questionSignature(id) != desiredQuestions.map { it.id to it.snapshot }
        if (changedQuestions && !confirmResetDrafts) throw AcademicConflict(current["revision"] as Int)
        var next = draft ?: current.toDraft()
        if (submitted) {
            val sameStructure = current["moduleId"] == next.moduleId &&
                current["kind"] == next.kind &&
                current["maxGrade"] == next.maxGrade &&
                current["required"] == next.required &&
                current["allowLate"] == next.allowLate &&
                !changedQuestions
            val oldDeadline = (current["deadline"] as String?)?.let(java.time.Instant::parse)
            val resolution = AssessmentEditPolicy.resolvePostSubmissionDeadline(sameStructure, oldDeadline, next.deadline)
            if (!resolution.allowed) throw AcademicConflict(current["revision"] as Int)
            next = next.copy(deadline = resolution.resolvedDeadline)
        }
        val now = Timestamp.from(clock.instant())
        if (desiredQuestions != null && changedQuestions) {
            resetPendingAttempts(id)
            jdbc.update("DELETE FROM core.assessment_questions WHERE assessment_id=?", id)
            deleteUnusedAssessmentImages(id, desiredQuestions)
            writeQuestions(id, desiredQuestions)
        }
        if (jdbc.update("""UPDATE core.assessments SET module_id=?,kind=?,deadline=?,allow_late=?,title=?,description=?,max_grade=?,required=?,
            revision=revision+1,updated_at=?,updated_by=?,questions_updated_at=CASE WHEN ? THEN ? ELSE questions_updated_at END
            WHERE id=? AND revision=?""", next.moduleId, next.kind, next.deadline?.let(Timestamp::from), next.allowLate, next.title, next.description, next.maxGrade, next.required,
            now, actorId, changedQuestions, now, id, expectedRevision) != 1) return null
        if (current["moduleId"] != next.moduleId) updatePublicationSubject(id, next.moduleId)
        audit(actorId, "academic.assessment_updated", id)
        val result = assessment(actorId, role, sessionHash, id) ?: return null
        idempotency.save(actorId, "updateAssessment", "/assessments/$id", key, requestHash, result)
        return result
    }

    @Transactional
    override fun publish(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int): Map<String, Any?>? {
        lockAuthInvariant()
        val moduleId = jdbc.query("SELECT module_id FROM core.assessments WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return null
        val module = lockModuleForWrite(moduleId, actorId, sessionHash, role) ?: return null
        val row = jdbc.query("SELECT * FROM core.assessments WHERE id=? FOR UPDATE", { rs, _ -> rs.assessmentBase() }, id).firstOrNull() ?: return null
        replay(actorId, "publishAssessment", "/assessments/$id/publish", key, requestHash)?.let { return it }
        if (row["revision"] != expectedRevision) return null
        if (row["status"] != "draft") throw AcademicConflict(row["revision"] as Int)
        val questionCount = questionSignature(id).size
        if (!AssessmentEditPolicy.canPublish(row["status"] as String, hasSubmissions(id), row["kind"] as String, questionCount)) throw AcademicConflict(row["revision"] as Int)
        val now = Timestamp.from(clock.instant())
        jdbc.update("UPDATE core.assessments SET status='published',published_at=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", now, now, actorId, id, expectedRevision)
        jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?) ON CONFLICT DO NOTHING", module.first, "assessment:$id")
        audit(actorId, "academic.assessment_published", id)
        val result = assessment(actorId, role, sessionHash, id) ?: return null
        idempotency.save(actorId, "publishAssessment", "/assessments/$id/publish", key, requestHash, result)
        return result
    }

    override fun assessment(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        requireSession(actorId, sessionHash)
        val where = accessPredicate(role)
        val row = jdbc.query("""SELECT a.*,m.subject_id,s.class_id,c.active AS class_active,
            (SELECT count(*)::integer FROM core.assessment_questions aq WHERE aq.assessment_id=a.id) AS question_count,
            EXISTS(SELECT 1 FROM core.enrollments e WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id)) AS assigned
            FROM core.assessments a JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
            WHERE a.id=? AND $where""", { rs, _ -> rs.assessmentBase() + mapOf("classActive" to rs.getBoolean("class_active"), "assigned" to rs.getBoolean("assigned"), "questionCount" to rs.getInt("question_count")) }, *(listOf(actorId, id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull() ?: return null
        return dto(row, actorId, role, sessionHash)
    }

    override fun assessments(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?): AssessmentPage {
        return assessmentPage(actorId, role, sessionHash, page, pageSize, sort, subjectId, moduleId, kind, status, false)
    }

    override fun dashboardAssessments(actorId: String, role: String, sessionHash: String, pageSize: Int): AssessmentPage =
        assessmentPage(actorId, role, sessionHash, 1, pageSize, "id", null, null, null, "published", true)

    private fun assessmentPage(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?, dashboard: Boolean): AssessmentPage {
        requireSession(actorId, sessionHash)
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
            $joins WHERE $where ORDER BY $order LIMIT ? OFFSET ?""", { rs, _ -> rs.assessmentBase() + mapOf("classActive" to rs.getBoolean("class_active"), "assigned" to rs.getBoolean("assigned"), "questionCount" to rs.getInt("question_count")) }, *(listOf(actorId) + args + listOf(pageSize, (page - 1) * pageSize)).toTypedArray())
        val ids = rows.map { it["id"] as String }
        val questionsById = if (role == "student") emptyMap() else questionRows(ids)
        val submissions = if (role == "student") emptySet() else hasSubmissions(ids)
        val items = rows.map { row ->
            val item = dto(row, actorId, role, sessionHash, questionsById[row["id"] as String].orEmpty(), (row["id"] as String) in submissions)
            if (dashboard) item + ("questionCount" to row["questionCount"]) else item
        }
        return AssessmentPage(items, page, pageSize, count)
    }

    @Transactional(readOnly = true)
    override fun pendingStudentCount(actorId: String, sessionHash: String): Int {
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

    private fun dto(row: Map<String, Any?>, actorId: String, role: String, sessionHash: String, loadedQuestions: List<Map<String, Any?>>? = null, loadedHasSubmissions: Boolean? = null): Map<String, Any?> {
        val id = row["id"] as String
        val questions = if (role == "student") emptyList() else loadedQuestions ?: questionRows(listOf(id))[id].orEmpty()
        val base = linkedMapOf<String, Any?>(
            "id" to id, "revision" to row["revision"], "createdAt" to row["createdAt"], "updatedAt" to row["updatedAt"], "updatedBy" to row["updatedBy"],
            "moduleId" to row["moduleId"], "title" to row["title"], "description" to row["description"], "kind" to row["kind"],
            "maxGrade" to row["maxGrade"], "deadline" to row["deadline"], "required" to row["required"], "allowLate" to row["allowLate"],
            "status" to row["status"], "publishedAt" to row["publishedAt"],
        )
        if (role == "student") {
            base["questionCount"] = row["questionCount"]
            base["assigned"] = row["assigned"] == true
        } else {
            base["questionIds"] = questions.map { it["id"] }
            base["questions"] = questions.map { it - setOf("sourceRevision", "contentHash") }
            base["hasSubmissions"] = loadedHasSubmissions ?: hasSubmissions(listOf(id)).contains(id)
        }
        return base
    }

    private fun questionRows(ids: List<String>): Map<String, List<Map<String, Any?>>> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        return jdbc.query("SELECT assessment_id,question_id,source,source_revision,content_hash,snapshot::text FROM core.assessment_questions WHERE assessment_id IN ($placeholders) ORDER BY assessment_id,position", { rs, _ ->
            @Suppress("UNCHECKED_CAST")
            rs.getString("assessment_id") to ((mapper.readValue(rs.getString("snapshot"), Map::class.java) as Map<String, Any?>) + mapOf("id" to rs.getString("question_id"), "origin" to if (rs.getString("source") == "bank") "bank" else "author", "sourceRevision" to rs.getObject("source_revision") as? Int, "contentHash" to rs.getString("content_hash")))
        }, *ids.toTypedArray()).groupBy({ it.first }, { it.second })
    }

    private fun writeQuestions(id: String, questions: List<AssessmentQuestion>) {
        questions.forEachIndexed { position, question ->
            val snapshot = snapshotImage(id, question)
            jdbc.update("""INSERT INTO core.assessment_questions(assessment_id,question_id,position,source,source_revision,content_hash,snapshot)
                VALUES(?,?,?,?,?,?,?::jsonb)""", id, question.id, position, question.source, question.sourceRevision, question.contentHash, mapper.writeValueAsString(snapshot))
        }
    }

    private fun snapshotImage(assessmentId: String, question: AssessmentQuestion): Map<String, Any?> {
        val snapshot = question.snapshot?.toMutableMap() ?: throw AcademicNotFound()
        if (question.source != "bank") return snapshot
        val image = snapshot["image"] as? Map<*, *> ?: return snapshot
        val sourceId = assessmentImageId(image["src"] as? String) ?: throw AcademicNotFound()
        val source = jdbc.query("SELECT purpose,parent_id,name,mime,size,sha256,content FROM core.files WHERE id=? AND status='attached'", { rs, _ ->
            listOf(rs.getString("purpose"), rs.getString("parent_id"), rs.getString("name"), rs.getString("mime"), rs.getLong("size"), rs.getString("sha256"), rs.getBytes("content"))
        }, sourceId).firstOrNull() ?: throw AcademicNotFound()
        if (source[0] == "assessment" && source[1] == assessmentId) return snapshot
        if (source[0] != "question") throw AcademicNotFound()
        val copiedId = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.files(id,purpose,parent_id,name,mime,size,sha256,status,content,created_at)
            VALUES(?,'assessment',?,?,?,?,?,'attached',?,?)""", copiedId, assessmentId, source[2], source[3], source[4], source[5], source[6], Timestamp.from(clock.instant()))
        snapshot["image"] = mapOf("src" to "/api/v1/files/$copiedId/content", "alt" to image["alt"])
        return snapshot
    }

    private fun deleteUnusedAssessmentImages(assessmentId: String, questions: List<AssessmentQuestion>) {
        val keep = questions.mapNotNull { question -> (question.snapshot?.get("image") as? Map<*, *>)?.get("src") as? String }
            .mapNotNull(::assessmentImageId).toSet()
        jdbc.query("SELECT id FROM core.files WHERE purpose='assessment' AND parent_id=?", { rs, _ -> rs.getString(1) }, assessmentId)
            .filterNot { it in keep }
            .forEach { jdbc.update("DELETE FROM core.files WHERE id=?", it) }
    }

    private fun assessmentImageId(src: String?): String? = Regex("/api/v1/files/([A-Za-z0-9][A-Za-z0-9._:-]{0,127})/content").matchEntire(src ?: "")?.groupValues?.get(1)

    private fun retainExistingSnapshots(id: String, questions: List<AssessmentQuestion>): List<AssessmentQuestion> = questions.map { question ->
        if (question.snapshot != null) question else jdbc.query("SELECT source_revision,content_hash,snapshot::text FROM core.assessment_questions WHERE assessment_id=? AND question_id=? AND source='bank'", { rs, _ ->
            @Suppress("UNCHECKED_CAST")
            question.copy(snapshot = mapper.readValue(rs.getString("snapshot"), Map::class.java) as Map<String, Any?>, sourceRevision = rs.getObject("source_revision") as? Int, contentHash = rs.getString("content_hash"))
        }, id, question.id).firstOrNull() ?: throw AcademicNotFound()
    }

    private fun questionSignature(id: String): List<Pair<String, Any?>> = jdbc.query("SELECT question_id,snapshot::text FROM core.assessment_questions WHERE assessment_id=? ORDER BY position", { rs, _ ->
        @Suppress("UNCHECKED_CAST")
        rs.getString(1) to mapper.readValue(rs.getString(2), Map::class.java) as Map<String, Any?>
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
        val subjectId = jdbc.queryForObject("SELECT subject_id FROM core.modules WHERE id=?", String::class.java, moduleId) ?: throw AcademicNotFound()
        jdbc.update("DELETE FROM core.subject_publications WHERE publication_id=?", "assessment:$id")
        if (jdbc.queryForObject("SELECT status='published' FROM core.assessments WHERE id=?", Boolean::class.java, id) == true) jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?)", subjectId, "assessment:$id")
    }

    private fun lockModuleForWrite(moduleId: String, actorId: String, sessionHash: String, role: String): Pair<String, Int>? {
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val found = jdbc.query("SELECT s.class_id FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=? AND m.withdrawn_at IS NULL", { rs, _ -> rs.getString(1) }, moduleId).firstOrNull() ?: return null
        val activeClass = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, found).firstOrNull() ?: return null
        if (currentRole == "teacher") jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, actorId)
        val subjectId = jdbc.query("SELECT id FROM core.subjects WHERE class_id=? AND id=(SELECT subject_id FROM core.modules WHERE id=?) FOR UPDATE", { rs, _ -> rs.getString(1) }, found, moduleId).firstOrNull() ?: return null
        val module = jdbc.query("SELECT revision FROM core.modules WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> rs.getInt(1) }, moduleId).firstOrNull() ?: return null
        val allowed = activeClass && (currentRole == "admin" || canWriteSubject(subjectId, actorId))
        requireSession(actorId, sessionHash, "admin", "teacher")
        if (!allowed) throw AcademicForbidden()
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
        return jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).firstOrNull() ?: throw AcademicForbidden()
    }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun replay(actorId: String, operation: String, target: String, key: String, requestHash: String): Map<String, Any?>? = try {
        idempotency.find(actorId, operation, target, key, requestHash)
    } catch (_: IdempotencyKeyReuse) { throw br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict() }
    private fun audit(actorId: String, eventType: String, id: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), eventType, id, actorId) }
    private fun ResultSet.assessmentBase() = mapOf(
        "id" to getString("id"), "revision" to getInt("revision"), "createdAt" to getTimestamp("created_at").toInstant().toString(), "updatedAt" to getTimestamp("updated_at").toInstant().toString(),
        "updatedBy" to getString("updated_by"), "moduleId" to getString("module_id"), "title" to getString("title"), "description" to getString("description"),
        "kind" to getString("kind"), "maxGrade" to getBigDecimal("max_grade")?.toDouble(), "deadline" to getTimestamp("deadline")?.toInstant()?.toString(),
        "required" to getBoolean("required"), "allowLate" to getBoolean("allow_late"), "status" to getString("status"), "publishedAt" to getTimestamp("published_at")?.toInstant()?.toString(),
    )
    private fun Map<String, Any?>.toDraft() = AssessmentDraft(moduleId = getValue("moduleId") as String, title = getValue("title") as String, description = getValue("description") as String, kind = getValue("kind") as String, maxGrade = get("maxGrade") as Double?, deadline = get("deadline")?.let { java.time.Instant.parse(it as String) }, required = getValue("required") as Boolean, allowLate = getValue("allowLate") as Boolean)
}
