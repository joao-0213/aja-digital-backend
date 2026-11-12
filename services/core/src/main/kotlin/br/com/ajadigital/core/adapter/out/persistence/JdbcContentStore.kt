package br.com.ajadigital.core.adapter.out.persistence

import br.com.ajadigital.core.application.academic.AcademicConflict
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.ports.out.ContentStore
import br.com.ajadigital.core.ports.out.AcademicPage
import br.com.ajadigital.core.ports.out.DownloadedFile
import br.com.ajadigital.core.ports.out.MaterialCreate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcContentStore(private val jdbc: JdbcTemplate, private val clock: Clock) : ContentStore {
    private val materialProjection = "SELECT mt.*,u.name author_name,f.id file_meta_id,f.name file_name,f.size file_size,f.mime file_mime,f.purpose file_purpose,f.parent_id file_parent_id,f.status file_status FROM core.materials mt JOIN core.users u ON u.id=mt.author_id LEFT JOIN core.files f ON f.id=mt.file_id"

    @Transactional
    override fun upload(actorId: String, role: String, sessionHash: String, key: String, name: String, declaredMime: String, purpose: String, parentId: String, bytes: ByteArray): Map<String, Any?> {
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash)
        when (purpose) {
            "material" -> if (lockModuleForWrite(parentId, actorId, sessionHash, currentRole) == null) throw AcademicNotFound()
            "forum" -> if (!canReadSubject(parentId, actorId, currentRole)) throw AcademicNotFound()
            "work" -> if (currentRole != "student" || !canPrepareWork(parentId, actorId, currentRole)) throw AcademicNotFound()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0)) IS NULL", Boolean::class.java, "$actorId|file|$key")
        val requestHash = digest("$purpose|$parentId|$name|$declaredMime|$hash")
        val prior = jdbc.query("SELECT request_hash,file_id FROM core.file_upload_keys WHERE owner_id=? AND idempotency_key=?", { rs, _ -> rs.getString(1) to rs.getString(2) }, actorId, key).firstOrNull()
        if (prior != null) {
            if (prior.first != requestHash) throw br.com.ajadigital.core.application.academic.AcademicIdempotencyConflict()
            return file(prior.second, actorId, currentRole, sessionHash) ?: throw AcademicNotFound()
        }
        val now = Timestamp.from(clock.instant())
        val id = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO core.files(id,owner_id,purpose,parent_id,name,mime,size,sha256,status,expires_at,content,created_at) VALUES(?,?,?,?,?,?,?,?, 'staged',?,?,?)", id, actorId, purpose, parentId, name, declaredMime, bytes.size, hash, Timestamp.from(clock.instant().plusSeconds(86400)), bytes, now)
        jdbc.update("INSERT INTO core.file_upload_keys(owner_id,idempotency_key,request_hash,file_id,created_at) VALUES(?,?,?,?,?)", actorId, key, requestHash, id, now)
        return file(id, actorId, currentRole, sessionHash) ?: throw AcademicNotFound()
    }

    @Transactional
    override fun provisionQuestionImage(id: String, questionId: String, name: String, mime: String, bytes: ByteArray): Map<String, Any?> {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0)) IS NULL", Boolean::class.java, "question-image|$id")
        val prior = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=? FOR UPDATE", { rs, _ -> fileRow(rs) }, id).firstOrNull()
        if (prior != null) {
            if (prior["purpose"] == "question" && prior["parentId"] == questionId && prior["name"] == name && prior["mime"] == mime && prior["sha256"] == hash) return fileDto(prior)
            throw AcademicConflict()
        }
        jdbc.update("INSERT INTO core.files(id,purpose,parent_id,name,mime,size,sha256,status,content,created_at) VALUES(?,'question',?,?,?,?,?,'attached',?,?)", id, questionId, name, mime, bytes.size, hash, bytes, Timestamp.from(clock.instant()))
        return fileDto(jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=?", { rs, _ -> fileRow(rs) }, id).first())
    }

    override fun file(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>? {
        requireSession(actorId, sessionHash)
        val file = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=?", { rs, _ -> fileRow(rs) }, id).firstOrNull() ?: return null
        if (file["status"] == "staged") {
            if (file["ownerId"] != actorId || (file["expiresAt"] as java.time.Instant) <= clock.instant() || !canUseStaged(file, actorId, role)) return null
        } else if (!canReadFile(id, actorId, role)) return null
        return fileDto(file)
    }

    override fun download(id: String, actorId: String, role: String, sessionHash: String, disposition: String?): DownloadedFile? {
        require(disposition == null || disposition in setOf("inline", "attachment"))
        requireSession(actorId, sessionHash)
        val file = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=?", { rs, _ -> fileRow(rs) }, id).firstOrNull() ?: return null
        if (file["status"] == "staged") {
            if (file["ownerId"] != actorId || (file["expiresAt"] as java.time.Instant) <= clock.instant() || !canUseStaged(file, actorId, role)) return null
        } else if (!canReadFile(id, actorId, role)) return null
        val bytes = jdbc.query("SELECT content FROM core.files WHERE id=?", { rs, _ -> rs.getBytes(1) }, id).firstOrNull() ?: return null
        val mime = file["mime"] as String
        val safeInline = file["purpose"] in setOf("material", "question") && mime == "application/pdf" || file["purpose"] == "forum" && mime.startsWith("image/")
        return DownloadedFile(file["name"] as String, mime, bytes, disposition == "inline" && safeInline || disposition == null && safeInline)
    }

    @Transactional
    override fun discard(id: String, actorId: String, role: String, sessionHash: String) {
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        val file = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=? FOR UPDATE", { rs, _ -> fileRow(rs) }, id).firstOrNull() ?: throw AcademicNotFound()
        if (file["ownerId"] != actorId || file["status"] != "staged" || (file["expiresAt"] as java.time.Instant) <= clock.instant()) throw AcademicConflict()
        if (!canUseStaged(file, actorId, role)) throw AcademicNotFound()
        jdbc.update("DELETE FROM core.files WHERE id=? AND status='staged'", id)
    }

    override fun materials(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, moduleId: String?, subjectId: String?): AcademicPage {
        requireSession(actorId, sessionHash)
        val where = buildList {
            add("EXISTS(SELECT 1 FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=mt.module_id AND m.withdrawn_at IS NULL AND ${subjectAccess("s", role)})")
            if (moduleId != null) add("mt.module_id=?")
            if (subjectId != null) add("EXISTS(SELECT 1 FROM core.modules m WHERE m.id=mt.module_id AND m.subject_id=?)")
        }.joinToString(" AND ")
        val args = mutableListOf<Any>().apply { if (role == "teacher" || role == "student") add(actorId); moduleId?.let(::add); subjectId?.let(::add) }
        val count = jdbc.queryForObject("SELECT count(*) FROM core.materials mt WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.query("$materialProjection WHERE $where ORDER BY mt.order_index${if (sort.startsWith("-")) " DESC" else " ASC"},mt.id LIMIT ? OFFSET ?", { rs, _ -> materialMap(rs) }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return AcademicPage(rows, page, size, count)
    }

    override fun material(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>? {
        requireSession(actorId, sessionHash)
        val result = jdbc.query("SELECT mt.*,u.name author_name,f.id file_meta_id,f.name file_name,f.size file_size,f.mime file_mime,f.purpose file_purpose,f.parent_id file_parent_id,f.status file_status,m.id module_key,m.revision module_revision,m.created_at module_created,m.updated_at module_updated,m.updated_by module_updated_by,m.subject_id,m.title module_title,m.description module_description,m.order_index module_order,s.class_id,s.name subject_name,s.color,s.icon,s.description subject_description,s.revision subject_revision,s.created_at subject_created,s.updated_at subject_updated,s.updated_by subject_updated_by,c.name class_name,c.active class_active FROM core.materials mt JOIN core.users u ON u.id=mt.author_id LEFT JOIN core.files f ON f.id=mt.file_id JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id WHERE mt.id=? AND ${subjectAccess("s", role)}", { rs, _ ->
            val material = materialMap(rs)
            mapOf("material" to material, "module" to mapOf("id" to rs.getString("module_key"), "revision" to rs.getInt("module_revision"), "createdAt" to rs.getTimestamp("module_created").toInstant().toString(), "updatedAt" to rs.getTimestamp("module_updated").toInstant().toString(), "updatedBy" to rs.getString("module_updated_by"), "subjectId" to rs.getString("subject_id"), "title" to rs.getString("module_title"), "description" to rs.getString("module_description"), "order" to rs.getInt("module_order")), "subject" to subjectSummary(rs, actorId, role), "canManageContent" to (rs.getBoolean("class_active") && (role == "admin" || role == "teacher" && canWriteSubject(rs.getString("subject_id"), actorId))))
        }, *(listOf(id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull()
        return result
    }

    @Transactional
    override fun authorizeMaterialPublication(actorId: String, role: String, sessionHash: String, moduleId: String) {
        lockAuthInvariant()
        if (lockModuleForWrite(moduleId, actorId, sessionHash, role) == null) throw AcademicNotFound()
    }

    @Transactional
    override fun publishMaterials(actorId: String, role: String, sessionHash: String, moduleId: String, expectedRevision: Int, items: List<MaterialCreate>): AcademicPage? {
        lockAuthInvariant()
        val module = lockModuleForWrite(moduleId, actorId, sessionHash, role) ?: return null
        if (module.second != expectedRevision) return null
        val current = jdbc.query("SELECT id,revision FROM core.materials WHERE module_id=? ORDER BY order_index,id FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getInt(2) }, moduleId)
        val files = items.mapNotNull { it.fileId }.distinct()
        if (files.size != items.mapNotNull { it.fileId }.size) throw AcademicNotFound()
        files.forEach { fileId ->
            val ok = jdbc.query("SELECT id FROM core.files WHERE id=? AND owner_id=? AND purpose='material' AND parent_id=? AND status='staged' AND expires_at>? FOR UPDATE", { rs, _ -> rs.getString(1) }, fileId, actorId, moduleId, Timestamp.from(clock.instant())).isNotEmpty()
            if (!ok) throw AcademicNotFound()
        }
        val now = Timestamp.from(clock.instant())
        val ids = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            val id = UUID.randomUUID().toString(); ids += id
            jdbc.update("INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index) VALUES(?,1,?,?,?,?,?,?,?,?,?,?,?,?)", id, now, now, actorId, actorId, moduleId, item.title.trim(), item.kind, item.duration, item.content, item.fileId, item.videoUrl, current.size + index)
            item.fileId?.let { jdbc.update("UPDATE core.files SET status='attached',expires_at=NULL WHERE id=?", it) }
        }
        jdbc.update("UPDATE core.modules SET revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", now, actorId, moduleId, expectedRevision)
        val subjectId = module.first
        ids.forEach { jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?) ON CONFLICT DO NOTHING", subjectId, "material:$it") }
        audit(actorId, "academic.materials_published", moduleId)
        val created = ids.mapNotNull { jdbc.query("$materialProjection WHERE mt.id=?", { rs, _ -> materialMap(rs) }, it).firstOrNull() }
        return AcademicPage(created, 1, created.size, created.size)
    }

    @Transactional
    override fun updateMaterial(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, title: String?, duration: Double?, content: String?, videoUrl: String?): Map<String, Any?>? {
        lockAuthInvariant()
        val moduleId = jdbc.query("SELECT module_id FROM core.materials WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return null
        val module = lockModuleForWrite(moduleId, actorId, sessionHash, role) ?: return null
        val current = jdbc.query("$materialProjection WHERE mt.id=? AND mt.module_id=? FOR UPDATE OF mt", { rs, _ -> materialMap(rs) }, id, moduleId).firstOrNull() ?: return null
        if (current["revision"] != expectedRevision) return null
        val kind = current["kind"] as String
        if (kind != "video" && videoUrl != null) throw IllegalArgumentException()
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.materials SET title=?,duration=?,content=?,video_url=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", title ?: current["title"], duration ?: current["duration"], content ?: current["content"], videoUrl ?: current["videoUrl"], now, actorId, id, expectedRevision) != 1) return null
        jdbc.update("UPDATE core.modules SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, moduleId)
        audit(actorId, "academic.material_updated", id)
        val detail = material(id, actorId, role, sessionHash) ?: return null
        val result = detail["material"] as? Map<*, *> ?: return null
        return result.entries.filter { it.key is String }.associate { it.key as String to it.value }
    }

    @Transactional
    override fun reorderMaterials(actorId: String, role: String, sessionHash: String, moduleId: String, expectedRevision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): AcademicPage? {
        lockAuthInvariant()
        val module = lockModuleForWrite(moduleId, actorId, sessionHash, role) ?: return null
        if (module.second != expectedRevision) return null
        val current = jdbc.query("SELECT id,revision FROM core.materials WHERE module_id=? ORDER BY id FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getInt(2) }, moduleId)
        if (items.size != current.size || items.map { it.first }.toSet() != current.map { it.first }.toSet() || items.any { item -> current.none { it == item } }) return null
        val now = Timestamp.from(clock.instant())
        items.forEachIndexed { index, item -> jdbc.update("UPDATE core.materials SET order_index=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", index, now, actorId, item.first, item.second) }
        jdbc.update("UPDATE core.modules SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, moduleId)
        audit(actorId, "academic.materials_reordered", moduleId)
        val ordered = jdbc.query("$materialProjection WHERE mt.module_id=? ORDER BY mt.order_index,mt.id", { rs, _ -> materialMap(rs) }, moduleId)
        return AcademicPage(ordered.drop((page - 1) * size).take(size), page, size, ordered.size)
    }

    override fun progress(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, subjectId: String?, materialId: String?): AcademicPage {
        requireSession(actorId, sessionHash)
        val where = buildString {
            append("p.user_id=? AND EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id WHERE mt.id=p.material_id AND ${subjectAccess("s", role)})")
            if (subjectId != null) append(" AND EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id WHERE mt.id=p.material_id AND m.subject_id=?)")
            if (materialId != null) append(" AND p.material_id=?")
        }
        val args = mutableListOf<Any>(actorId).apply { if (role == "teacher" || role == "student") add(actorId); subjectId?.let(::add); materialId?.let(::add) }
        val count = jdbc.queryForObject("SELECT count(*) FROM core.material_progress p WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.query("SELECT p.* FROM core.material_progress p WHERE $where ORDER BY p.id${if (sort.startsWith("-")) " DESC" else " ASC"} LIMIT ? OFFSET ?", { rs, _ -> progressMap(rs) }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return AcademicPage(rows, page, size, count)
    }

    @Transactional
    override fun recordProgress(actorId: String, role: String, sessionHash: String, materialId: String, completed: Boolean): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, sessionHash)
        val subjectId = jdbc.query("SELECT s.id FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id WHERE mt.id=?", { rs, _ -> rs.getString(1) }, materialId).firstOrNull() ?: return null
        if (!canReadSubject(subjectId, actorId, role)) return null
        val existing = jdbc.query("SELECT * FROM core.material_progress WHERE user_id=? AND material_id=? FOR UPDATE", { rs, _ -> progressMap(rs) }, actorId, materialId).firstOrNull()
        val now = Timestamp.from(clock.instant())
        val id = existing?.get("id") as? String ?: UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.material_progress(id,revision,created_at,updated_at,updated_by,user_id,material_id,completed,last_access)
            VALUES(?,1,?,?,?,?,?,?,?) ON CONFLICT(user_id,material_id) DO UPDATE SET revision=core.material_progress.revision+1,updated_at=EXCLUDED.updated_at,updated_by=EXCLUDED.updated_by,completed=core.material_progress.completed OR EXCLUDED.completed,last_access=EXCLUDED.last_access""", id, now, now, actorId, actorId, materialId, completed, now)
        return jdbc.query("SELECT * FROM core.material_progress WHERE user_id=? AND material_id=?", { rs, _ -> progressMap(rs) }, actorId, materialId).first()
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    fun cleanExpiredPreparations() { jdbc.update("DELETE FROM core.files WHERE status='staged' AND expires_at<=?", Timestamp.from(clock.instant())) }

    private fun lockModuleForWrite(moduleId: String, actorId: String, sessionHash: String, role: String): Pair<String, Int>? {
        val roleNow = requireSession(actorId, sessionHash, "admin", "teacher")
        val classId = jdbc.query("SELECT s.class_id FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=? AND m.withdrawn_at IS NULL", { rs, _ -> rs.getString(1) }, moduleId).firstOrNull() ?: return null
        if (jdbc.query("SELECT id FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, classId).isEmpty()) return null
        if (roleNow == "teacher") jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, actorId)
        val subject = jdbc.query("SELECT s.id,s.class_id FROM core.subjects s WHERE s.id=(SELECT subject_id FROM core.modules WHERE id=?) FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getString(2) }, moduleId).firstOrNull() ?: return null
        val module = jdbc.query("SELECT revision FROM core.modules WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> rs.getInt(1) }, moduleId).firstOrNull() ?: return null
        requireSession(actorId, sessionHash, "admin", "teacher")
        val allowed = roleNow == "admin" && jdbc.queryForObject("SELECT active FROM core.classes WHERE id=?", Boolean::class.java, classId) == true || roleNow == "teacher" && canWriteSubject(subject.first, actorId)
        if (!allowed) throw AcademicForbidden()
        return subject.first to module
    }

    private fun canReadSubject(subjectId: String, actorId: String, role: String): Boolean {
        val query = "SELECT EXISTS(SELECT 1 FROM core.subjects s WHERE s.id=? AND ${subjectAccess("s", role)})"
        return jdbc.queryForObject(query, Boolean::class.java, *(listOf(subjectId) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()) == true
    }
    private fun canWriteSubject(subjectId: String, actorId: String) = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id WHERE s.id=? AND c.active AND (EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND u.active AND u.role='teacher')))", Boolean::class.java, subjectId, actorId) == true
    private fun subjectAccess(subject: String, role: String) = when (role) {
        "admin" -> "true"
        "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=$subject.id AND st.teacher_id=? AND u.active AND u.role='teacher')"
        "student" -> "EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=$subject.class_id AND e.status='active' AND e.student_id=? AND u.active AND u.email_confirmed_at IS NOT NULL)"
        else -> "false"
    }
    private fun canUseStaged(file: Map<String, Any?>, actorId: String, role: String): Boolean = when (file["purpose"]) {
        "material" -> role in setOf("teacher", "admin") && canWriteModule(file["parentId"] as String, actorId, role)
        "work" -> canPrepareWork(file["parentId"] as String, actorId, role)
        "forum" -> canReadSubject(file["parentId"] as String, actorId, role)
        else -> false
    }
    private fun canWriteModule(moduleId: String, actorId: String, role: String): Boolean {
        val module = jdbc.query("SELECT s.id FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id WHERE m.id=? AND m.withdrawn_at IS NULL AND c.active AND ${if (role == "admin") "true" else "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND u.active AND u.role='teacher')"}", { rs, _ -> rs.getString(1) }, *(listOf(moduleId) + if (role == "teacher") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull()
        return module != null
    }
    private fun canPrepareWork(assessmentId: String, actorId: String, role: String): Boolean {
        if (role != "student") return false
        return jdbc.queryForObject("""SELECT EXISTS(
            SELECT 1 FROM core.assessments a
            JOIN core.modules m ON m.id=a.module_id AND m.withdrawn_at IS NULL
            JOIN core.subjects s ON s.id=m.subject_id
            JOIN core.classes c ON c.id=s.class_id AND c.active
            JOIN core.enrollments e ON e.class_id=c.id AND e.student_id=? AND e.status='active'
            JOIN core.users u ON u.id=e.student_id AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL
            WHERE a.id=? AND a.kind='work' AND a.status='published' AND a.published_at<=?
              AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id)
              AND (a.deadline IS NULL OR a.allow_late OR ?<a.deadline)
              AND (a.deadline IS NULL OR e.enrolled_at IS NULL OR e.enrolled_at<=a.deadline)
        )""", Boolean::class.java, actorId, assessmentId, Timestamp.from(clock.instant()), Timestamp.from(clock.instant())) == true
    }
    private fun canReadFile(id: String, actorId: String, role: String): Boolean {
        val assessmentAccess = when (role) {
            "admin" -> "true"
            "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND u.active AND u.role='teacher')"
            "student" -> "a.status='published' AND c.active AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=c.id AND e.student_id=? AND e.status='active' AND u.active AND u.email_confirmed_at IS NOT NULL AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id))"
            else -> "false"
        }
        val args = listOf(id) + (if (role == "teacher" || role == "student") listOf(actorId) else emptyList()) + (if (role == "teacher" || role == "student") listOf(actorId) else emptyList())
        return jdbc.queryForObject("""SELECT EXISTS(
            SELECT 1 FROM core.files f WHERE f.id=? AND f.status='attached' AND (
                f.purpose='question'
                OR (f.purpose='assessment' AND EXISTS(
                    SELECT 1 FROM core.assessments a JOIN core.modules m ON m.id=a.module_id AND m.withdrawn_at IS NULL
                    JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
                    WHERE a.id=f.parent_id AND $assessmentAccess
                ))
                OR EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id WHERE mt.file_id=f.id AND ${subjectAccess("s", role)})
            ))""", Boolean::class.java, *args.toTypedArray()) == true
    }

    private fun fileRow(rs: ResultSet) = mapOf("id" to rs.getString("id"), "ownerId" to rs.getString("owner_id"), "purpose" to rs.getString("purpose"), "parentId" to rs.getString("parent_id"), "name" to rs.getString("name"), "mime" to rs.getString("mime"), "size" to rs.getLong("size"), "sha256" to rs.getString("sha256"), "status" to rs.getString("status"), "expiresAt" to rs.getTimestamp("expires_at")?.toInstant())
    private fun fileDto(file: Map<String, Any?>) = mapOf("id" to file["id"], "name" to file["name"], "size" to file["size"], "mime" to file["mime"], "purpose" to if (file["purpose"] == "assessment") "question" else file["purpose"], "parentId" to file["parentId"], "status" to file["status"], "downloadUrl" to "/api/v1/files/${file["id"]}/content")
    private fun materialMap(rs: ResultSet): Map<String, Any?> {
        val fileId = rs.getString("file_id")
        val authorId = rs.getString("author_id")
        val file = fileId?.let { fileDto(mapOf("id" to rs.getString("file_meta_id"), "name" to rs.getString("file_name"), "size" to rs.getLong("file_size"), "mime" to rs.getString("file_mime"), "purpose" to rs.getString("file_purpose"), "parentId" to rs.getString("file_parent_id"), "status" to rs.getString("file_status"))) }
        return mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(), "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "moduleId" to rs.getString("module_id"), "title" to rs.getString("title"), "kind" to rs.getString("kind"), "duration" to rs.getBigDecimal("duration").toDouble(), "content" to rs.getString("content"), "fileId" to fileId, "videoUrl" to rs.getString("video_url"), "file" to file, "author" to mapOf("id" to authorId, "name" to rs.getString("author_name")), "order" to rs.getInt("order_index"))
    }
    private fun subjectSummary(rs: ResultSet, actorId: String, role: String): Map<String, Any?> {
        val id = rs.getString("subject_id")
        val teachers = jdbc.query("SELECT u.id,u.name FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=? ORDER BY u.id", { r, _ -> mapOf("id" to r.getString(1), "name" to r.getString(2)) }, id)
        val publications = jdbc.query("SELECT publication_id FROM core.subject_publications WHERE subject_id=? ORDER BY publication_id", { r, _ -> r.getString(1) }, id)
        val seen = jdbc.query("SELECT jsonb_array_elements_text(publication_ids) FROM core.subject_visits WHERE subject_id=? AND user_id=?", { r, _ -> r.getString(1) }, id, actorId)
        return mapOf("id" to id, "revision" to rs.getInt("subject_revision"), "createdAt" to rs.getTimestamp("subject_created").toInstant().toString(), "updatedAt" to rs.getTimestamp("subject_updated").toInstant().toString(), "updatedBy" to rs.getString("subject_updated_by"), "classId" to rs.getString("class_id"), "name" to rs.getString("subject_name"), "teacherIds" to teachers.map { it["id"] }, "color" to rs.getString("color"), "icon" to rs.getString("icon"), "description" to rs.getString("subject_description"), "className" to rs.getString("class_name"), "teachers" to teachers, "canManageContent" to (rs.getBoolean("class_active") && (role == "admin" || role == "teacher" && teachers.any { it["id"] == actorId })), "hasUpdates" to (publications - seen.toSet()).isNotEmpty(), "publicationIds" to publications)
    }
    private fun progressMap(rs: ResultSet) = mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(), "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "userId" to rs.getString("user_id"), "materialId" to rs.getString("material_id"), "completed" to rs.getBoolean("completed"), "lastAccess" to rs.getTimestamp("last_access").toInstant().toString())
    private fun audit(actorId: String, type: String, targetId: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), type, targetId, actorId) }
    private fun requireSession(actorId: String, hash: String, vararg roles: String): String {
        val accepted = if (roles.isEmpty()) listOf("admin", "teacher", "student") else roles.toList()
        val placeholders = accepted.joinToString(",") { "?" }
        return jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).firstOrNull() ?: throw AcademicForbidden()
    }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
