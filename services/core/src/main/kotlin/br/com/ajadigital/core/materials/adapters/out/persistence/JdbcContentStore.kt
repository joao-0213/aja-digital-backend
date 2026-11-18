package br.com.ajadigital.core.materials.adapters.out.persistence

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.application.contracts.DashboardContent
import br.com.ajadigital.core.materials.application.contracts.LearningProgress
import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.contracts.ResumeMaterial
import br.com.ajadigital.core.materials.application.models.DiscardFileCommand
import br.com.ajadigital.core.materials.application.models.DownloadFileQuery
import br.com.ajadigital.core.materials.application.models.DownloadedFile
import br.com.ajadigital.core.materials.application.models.FileDisposition
import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.FilePurpose
import br.com.ajadigital.core.materials.application.models.FileStatus
import br.com.ajadigital.core.materials.application.models.GetFileMetadataQuery
import br.com.ajadigital.core.materials.application.models.GetMaterialQuery
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.MaterialDetail
import br.com.ajadigital.core.materials.application.models.MaterialModule
import br.com.ajadigital.core.materials.application.models.MaterialSubject
import br.com.ajadigital.core.materials.application.models.NamedPerson
import br.com.ajadigital.core.materials.application.models.ReorderMaterialsCommand
import br.com.ajadigital.core.materials.application.models.StoreMaterialsQuery
import br.com.ajadigital.core.materials.application.models.StoreProgressQuery
import br.com.ajadigital.core.materials.application.models.StorePublishMaterialsCommand
import br.com.ajadigital.core.materials.application.models.StoreQuestionImageCommand
import br.com.ajadigital.core.materials.application.models.StoreRecordProgressCommand
import br.com.ajadigital.core.materials.application.models.StoreUpdateMaterialCommand
import br.com.ajadigital.core.materials.application.models.StoreUploadCommand
import br.com.ajadigital.core.materials.application.models.UploadPurpose
import br.com.ajadigital.core.materials.application.ports.out.FileStoreOutputPort
import br.com.ajadigital.core.materials.application.ports.out.LearningProgressOutputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialReadOutputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.application.ports.out.QuestionImageOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsConflict
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsForbidden
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsIdempotencyConflict
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.models.FilePreparation
import br.com.ajadigital.core.materials.domain.models.MaterialRevision
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import br.com.ajadigital.core.materials.domain.models.ProgressAction
import br.com.ajadigital.core.materials.domain.models.QuestionImageIdentity
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.Page
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JdbcContentStore(private val jdbc: JdbcTemplate, private val clock: Clock) : FileStoreOutputPort, QuestionImageOutputPort,
    MaterialReadOutputPort, MaterialWriteOutputPort, LearningProgressOutputPort {
    private val materialProjection = "SELECT mt.*,u.name author_name,f.id file_meta_id,f.name file_name,f.size file_size,f.mime file_mime,f.purpose file_purpose,f.parent_id file_parent_id,f.status file_status FROM core.materials mt JOIN core.users u ON u.id=mt.author_id LEFT JOIN core.files f ON f.id=mt.file_id"

    @Transactional
    override fun upload(request: StoreUploadCommand): FileMetadata {
        val actor = request.actor
        val actorId = actor.id
        val sessionHash = actor.sessionValidationReference
        val key = request.key
        val name = request.name
        val declaredMime = request.mime
        val purpose = request.purpose
        val parentId = request.parentId
        val bytes = request.content.copy()
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash)
        when (purpose) {
            UploadPurpose.MATERIAL -> if (lockModuleForWrite(parentId, actorId, sessionHash) == null) throw MaterialsNotFound()
            UploadPurpose.FORUM -> if (!canWriteForum(parentId, actorId, currentRole)) throw MaterialsNotFound()
            UploadPurpose.WORK -> if (currentRole != "student" || !canPrepareWork(parentId, actorId, currentRole)) throw MaterialsNotFound()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0)) IS NULL", Boolean::class.java, "$actorId|file|$key")
        val requestHash = digest("${purpose.value}|$parentId|$name|$declaredMime|$hash")
        val prior = jdbc.query("SELECT request_hash,file_id FROM core.file_upload_keys WHERE owner_id=? AND idempotency_key=?", { rs, _ -> rs.getString(1) to rs.getString(2) }, actorId, key).firstOrNull()
        if (prior != null) {
            if (prior.first != requestHash) throw MaterialsIdempotencyConflict()
            return file(GetFileMetadataQuery(actor, prior.second)) ?: throw MaterialsNotFound()
        }
        val now = Timestamp.from(clock.instant())
        val id = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO core.files(id,owner_id,purpose,parent_id,name,mime,size,sha256,status,expires_at,content,created_at) VALUES(?,?,?,?,?,?,?,?, 'staged',?,?,?)", id, actorId, purpose.value, parentId, name, declaredMime, bytes.size, hash, Timestamp.from(clock.instant().plusSeconds(86400)), bytes, now)
        jdbc.update("INSERT INTO core.file_upload_keys(owner_id,idempotency_key,request_hash,file_id,created_at) VALUES(?,?,?,?,?)", actorId, key, requestHash, id, now)
        return file(GetFileMetadataQuery(actor, id)) ?: throw MaterialsNotFound()
    }

    @Transactional
    override fun provisionQuestionImage(request: StoreQuestionImageCommand): FileMetadata {
        val id = request.id
        val questionId = request.questionId
        val name = request.name
        val mime = request.mime
        val bytes = request.content.copy()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0)) IS NULL", Boolean::class.java, "question-image|$id")
        val prior = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=? FOR UPDATE", { rs, _ -> fileRow(rs) }, id).firstOrNull()
        if (prior != null) {
            QuestionImageIdentity(questionId, name, mime, hash).verifyReplay(prior.questionImageIdentity())
            return prior.metadata
        }
        jdbc.update("INSERT INTO core.files(id,purpose,parent_id,name,mime,size,sha256,status,content,created_at) VALUES(?,'question',?,?,?,?,?,'attached',?,?)", id, questionId, name, mime, bytes.size, hash, bytes, Timestamp.from(clock.instant()))
        return jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=?", { rs, _ -> fileRow(rs) }, id).first().metadata
    }

    override fun file(request: GetFileMetadataQuery): FileMetadata? {
        val (actor, id) = request
        val actorId = actor.id
        val role = requireSession(actorId, actor.sessionValidationReference)
        val file = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=?", { rs, _ -> fileRow(rs) }, id).firstOrNull() ?: return null
        if (file.preparation != null) {
            if (!file.preparation.isOwnedBy(actorId, clock.instant()) || !canUseStaged(file.metadata, actorId, role)) return null
        } else if (!canReadFile(id, actorId, role)) return null
        return file.metadata
    }

    override fun download(request: DownloadFileQuery): DownloadedFile? {
        val (actor, id, disposition) = request
        val actorId = actor.id
        val role = requireSession(actorId, actor.sessionValidationReference)
        val file = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=?", { rs, _ -> fileRow(rs) }, id).firstOrNull() ?: return null
        if (file.preparation != null) {
            if (!file.preparation.isOwnedBy(actorId, clock.instant()) || !canUseStaged(file.metadata, actorId, role)) return null
        } else if (!canReadFile(id, actorId, role)) return null
        val bytes = jdbc.query("SELECT content FROM core.files WHERE id=?", { rs, _ -> rs.getBytes(1) }, id).firstOrNull() ?: return null
        val metadata = file.metadata
        val mime = metadata.mime
        val safeInline = metadata.purpose in setOf(FilePurpose.MATERIAL, FilePurpose.QUESTION) && mime == "application/pdf" ||
            metadata.purpose == FilePurpose.FORUM && mime.startsWith("image/")
        return DownloadedFile(metadata.name, mime, bytes, disposition != FileDisposition.ATTACHMENT && safeInline)
    }

    @Transactional
    override fun discard(request: DiscardFileCommand) {
        val (actor, id) = request
        val actorId = actor.id
        lockAuthInvariant()
        val role = requireSession(actorId, actor.sessionValidationReference)
        val file = jdbc.query("SELECT id,purpose,parent_id,name,mime,size,sha256,status,expires_at,owner_id FROM core.files WHERE id=? FOR UPDATE", { rs, _ -> fileRow(rs) }, id).firstOrNull() ?: throw MaterialsNotFound()
        if (file.preparation?.isOwnedBy(actorId, clock.instant()) != true) throw MaterialsConflict()
        if (!canUseStaged(file.metadata, actorId, role)) throw MaterialsNotFound()
        jdbc.update("DELETE FROM core.files WHERE id=? AND status='staged'", id)
    }

    override fun materials(request: StoreMaterialsQuery): Page<Material> {
        val (actor, page, size, sort, moduleId, subjectId) = request
        val actorId = actor.id
        val role = requireSession(actorId, actor.sessionValidationReference)
        val where = buildList {
            add("EXISTS(SELECT 1 FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=mt.module_id AND m.withdrawn_at IS NULL AND ${subjectAccess("s", role)})")
            if (moduleId != null) add("mt.module_id=?")
            if (subjectId != null) add("EXISTS(SELECT 1 FROM core.modules m WHERE m.id=mt.module_id AND m.subject_id=?)")
        }.joinToString(" AND ")
        val args = mutableListOf<Any>().apply { if (role == "teacher" || role == "student") add(actorId); moduleId?.let(::add); subjectId?.let(::add) }
        val count = jdbc.queryForObject("SELECT count(*) FROM core.materials mt WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.query("$materialProjection WHERE $where ORDER BY mt.order_index${if (sort.value.startsWith("-")) " DESC" else " ASC"},mt.id LIMIT ? OFFSET ?", { rs, _ -> materialRow(rs) }, *(args + listOf(size, pageOffset(page, size))).toTypedArray())
        return Page(rows, page, size, count)
    }

    override fun material(request: GetMaterialQuery): MaterialDetail? {
        val (actor, id) = request
        val actorId = actor.id
        val role = requireSession(actorId, actor.sessionValidationReference)
        val result = jdbc.query("SELECT mt.*,u.name author_name,f.id file_meta_id,f.name file_name,f.size file_size,f.mime file_mime,f.purpose file_purpose,f.parent_id file_parent_id,f.status file_status,m.id module_key,m.revision module_revision,m.created_at module_created,m.updated_at module_updated,m.updated_by module_updated_by,m.subject_id,m.title module_title,m.description module_description,m.order_index module_order,s.class_id,s.name subject_name,s.color,s.icon,s.description subject_description,s.revision subject_revision,s.created_at subject_created,s.updated_at subject_updated,s.updated_by subject_updated_by,c.name class_name,c.active class_active FROM core.materials mt JOIN core.users u ON u.id=mt.author_id LEFT JOIN core.files f ON f.id=mt.file_id JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id WHERE mt.id=? AND ${subjectAccess("s", role)}", { rs, _ ->
            val material = materialRow(rs)
            MaterialDetail(
                material,
                MaterialModule(rs.getString("module_key"), rs.getInt("module_revision"), rs.getTimestamp("module_created").toInstant().toString(),
                    rs.getTimestamp("module_updated").toInstant().toString(), rs.getString("module_updated_by"), rs.getString("subject_id"),
                    rs.getString("module_title"), rs.getString("module_description"), rs.getInt("module_order")),
                subjectSummary(rs, actorId, role),
                rs.getBoolean("class_active") && (role == "admin" || role == "teacher" && canWriteSubject(rs.getString("subject_id"), actorId)),
            )
        }, *(listOf(id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull()
        return result
    }

    @Transactional
    override fun authorizeMaterialPublication(actor: AuthenticatedActor, moduleId: String) {
        lockAuthInvariant()
        if (lockModuleForWrite(moduleId, actor.id, actor.sessionValidationReference) == null) throw MaterialsNotFound()
    }

    @Transactional
    override fun publishMaterials(request: StorePublishMaterialsCommand): Page<Material> {
        val actorId = request.actor.id
        val sessionHash = request.actor.sessionValidationReference
        val moduleId = request.moduleId
        val expectedRevision = request.expectedRevision
        val items = request.items
        lockAuthInvariant()
        val module = lockModuleForWrite(moduleId, actorId, sessionHash) ?: throw MaterialsConflict()
        MaterialRules.requireCurrentRevision(module.revision, expectedRevision)
        val current = jdbc.query("SELECT id,revision FROM core.materials WHERE module_id=? ORDER BY order_index,id FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getInt(2) }, moduleId)
        val files = items.mapNotNull { (it.source as? NewMaterial.Source.Pdf)?.fileId }
        MaterialRules.requireDistinctFiles(files)
        files.forEach { fileId ->
            val ok = jdbc.query("SELECT id FROM core.files WHERE id=? AND owner_id=? AND purpose='material' AND parent_id=? AND status='staged' AND expires_at>? FOR UPDATE", { rs, _ -> rs.getString(1) }, fileId, actorId, moduleId, Timestamp.from(clock.instant())).isNotEmpty()
            if (!ok) throw MaterialsNotFound()
        }
        val now = Timestamp.from(clock.instant())
        val ids = mutableListOf<String>()
        items.forEachIndexed { index, item ->
            val id = UUID.randomUUID().toString(); ids += id
            val kind = when (item.source) {
                NewMaterial.Source.Lesson -> MaterialKind.LESSON
                is NewMaterial.Source.Pdf -> MaterialKind.PDF
                is NewMaterial.Source.Video -> MaterialKind.VIDEO
            }
            val fileId = (item.source as? NewMaterial.Source.Pdf)?.fileId
            val videoUrl = (item.source as? NewMaterial.Source.Video)?.url
            jdbc.update("INSERT INTO core.materials(id,revision,created_at,updated_at,updated_by,author_id,module_id,title,kind,duration,content,file_id,video_url,order_index) VALUES(?,1,?,?,?,?,?,?,?,?,?,?,?,?)", id, now, now, actorId, actorId, moduleId, item.title.trim(), kind.value, item.duration, item.content, fileId, videoUrl, current.size + index)
            fileId?.let { jdbc.update("UPDATE core.files SET status='attached',expires_at=NULL WHERE id=?", it) }
        }
        jdbc.update("UPDATE core.modules SET revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", now, actorId, moduleId, expectedRevision)
        val subjectId = module.subjectId
        ids.forEach { jdbc.update("INSERT INTO core.subject_publications(subject_id,publication_id) VALUES(?,?) ON CONFLICT DO NOTHING", subjectId, "material:$it") }
        audit(actorId, "academic.materials_published", moduleId)
        val created = ids.map { jdbc.query("$materialProjection WHERE mt.id=?", { rs, _ -> materialRow(rs) }, it).single() }
        return Page(created, 1, created.size, created.size)
    }

    @Transactional
    override fun updateMaterial(request: StoreUpdateMaterialCommand): Material {
        val (actor, id, expectedRevision, title, duration, content, videoUrl) = request
        val actorId = actor.id
        val sessionHash = actor.sessionValidationReference
        lockAuthInvariant()
        val moduleId = jdbc.query("SELECT module_id FROM core.materials WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: throw MaterialsConflict()
        lockModuleForWrite(moduleId, actorId, sessionHash) ?: throw MaterialsConflict()
        val current = jdbc.query("$materialProjection WHERE mt.id=? AND mt.module_id=? FOR UPDATE OF mt", { rs, _ -> materialRow(rs) }, id, moduleId).firstOrNull() ?: throw MaterialsConflict()
        MaterialRules.requireCurrentRevision(current.revision, expectedRevision)
        MaterialRules.requireVideoEdit(current.kind == MaterialKind.VIDEO, videoUrl)
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.materials SET title=?,duration=?,content=?,video_url=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", title ?: current.title, duration ?: current.duration, content ?: current.content, videoUrl ?: current.videoUrl, now, actorId, id, expectedRevision) != 1) throw MaterialsConflict()
        jdbc.update("UPDATE core.modules SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, moduleId)
        audit(actorId, "academic.material_updated", id)
        return material(GetMaterialQuery(actor, id))?.material ?: throw MaterialsConflict()
    }

    @Transactional
    override fun reorderMaterials(request: ReorderMaterialsCommand): Page<Material> {
        val actorId = request.actor.id
        val sessionHash = request.actor.sessionValidationReference
        val moduleId = request.moduleId
        val expectedRevision = request.expectedRevision
        val items = request.items
        val page = request.page
        val size = request.size
        lockAuthInvariant()
        val module = lockModuleForWrite(moduleId, actorId, sessionHash) ?: throw MaterialsConflict()
        MaterialRules.requireCurrentRevision(module.revision, expectedRevision)
        val current = jdbc.query("SELECT id,revision FROM core.materials WHERE module_id=? ORDER BY id FOR UPDATE", { rs, _ -> MaterialRevision(rs.getString(1), rs.getInt(2)) }, moduleId)
        if (!MaterialRules.matchesCurrentOrder(items, current)) throw MaterialsConflict()
        val now = Timestamp.from(clock.instant())
        items.forEachIndexed { index, item -> jdbc.update("UPDATE core.materials SET order_index=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", index, now, actorId, item.id, item.revision) }
        jdbc.update("UPDATE core.modules SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, moduleId)
        audit(actorId, "academic.materials_reordered", moduleId)
        val ordered = jdbc.query("$materialProjection WHERE mt.module_id=? ORDER BY mt.order_index,mt.id", { rs, _ -> materialRow(rs) }, moduleId)
        val from = pageOffset(page, size).coerceAtMost(ordered.size.toLong()).toInt()
        return Page(ordered.drop(from).take(size), page, size, ordered.size)
    }

    override fun progress(request: StoreProgressQuery): Page<LearningProgress> {
        val (actor, page, size, sort, subjectId, materialId) = request
        val actorId = actor.id
        val role = requireSession(actorId, actor.sessionValidationReference)
        val where = buildString {
            append("p.user_id=? AND EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id WHERE mt.id=p.material_id AND ${subjectAccess("s", role)})")
            if (subjectId != null) append(" AND EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id WHERE mt.id=p.material_id AND m.subject_id=?)")
            if (materialId != null) append(" AND p.material_id=?")
        }
        val args = mutableListOf<Any>(actorId).apply { if (role == "teacher" || role == "student") add(actorId); subjectId?.let(::add); materialId?.let(::add) }
        val count = jdbc.queryForObject("SELECT count(*) FROM core.material_progress p WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.query("SELECT p.* FROM core.material_progress p WHERE $where ORDER BY p.id${if (sort.value.startsWith("-")) " DESC" else " ASC"} LIMIT ? OFFSET ?", { rs, _ -> progressRow(rs) }, *(args + listOf(size, pageOffset(page, size))).toTypedArray())
        return Page(rows, page, size, count)
    }

    private fun pageOffset(page: Int, size: Int): Long = (page.toLong() - 1) * size

    @Transactional(readOnly = true)
    override fun dashboard(actor: AuthenticatedActor, progressLimit: Int): DashboardContent {
        val actorId = actor.id
        require(progressLimit in 1..100)
        val role = requireSession(actorId, actor.sessionValidationReference, "student")
        val scope = "md.withdrawn_at IS NULL AND ${subjectAccess("s", role)}"
        val scopeArgs = if (role == "student" || role == "teacher") listOf(actorId) else emptyList()
        val totals = jdbc.query("""SELECT count(DISTINCT m.id),count(DISTINCT m.id) FILTER (WHERE p.completed)
            FROM core.materials m JOIN core.modules md ON md.id=m.module_id AND md.withdrawn_at IS NULL
            JOIN core.subjects s ON s.id=md.subject_id
            LEFT JOIN core.material_progress p ON p.material_id=m.id AND p.user_id=?
            WHERE $scope""", { rs, _ -> rs.getInt(1) to rs.getInt(2) }, *(listOf(actorId) + scopeArgs).toTypedArray()).single()
        val progress = jdbc.query("""SELECT p.* FROM core.material_progress p
            JOIN core.materials m ON m.id=p.material_id
            JOIN core.modules md ON md.id=m.module_id AND md.withdrawn_at IS NULL
            JOIN core.subjects s ON s.id=md.subject_id
            WHERE p.user_id=? AND $scope ORDER BY p.id DESC LIMIT ?""", { rs, _ -> progressRow(rs) }, *(listOf<Any>(actorId) + scopeArgs + progressLimit).toTypedArray())
        val resume = jdbc.query("""SELECT m.id,m.title,m.module_id,m.kind FROM core.material_progress p
            JOIN core.materials m ON m.id=p.material_id
            JOIN core.modules md ON md.id=m.module_id AND md.withdrawn_at IS NULL
            JOIN core.subjects s ON s.id=md.subject_id
            WHERE p.user_id=? AND NOT p.completed AND $scope ORDER BY p.last_access DESC,p.id DESC LIMIT 1""", { rs, _ ->
            ResumeMaterial(rs.getString("id"), rs.getString("title"), rs.getString("module_id"), storedKind(rs.getString("kind")))
        }, *(listOf(actorId) + scopeArgs).toTypedArray()).firstOrNull()
        return DashboardContent(progress, totals.second, totals.first, resume)
    }

    @Transactional
    override fun recordProgress(request: StoreRecordProgressCommand): LearningProgress? {
        val (actor, materialId, action) = request
        val actorId = actor.id
        lockAuthInvariant()
        val role = requireSession(actorId, actor.sessionValidationReference)
        val subjectId = jdbc.query("SELECT s.id FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id WHERE mt.id=?", { rs, _ -> rs.getString(1) }, materialId).firstOrNull() ?: return null
        if (!canReadSubject(subjectId, actorId, role)) return null
        val existing = jdbc.query("SELECT * FROM core.material_progress WHERE user_id=? AND material_id=? FOR UPDATE", { rs, _ -> progressRow(rs) }, actorId, materialId).firstOrNull()
        val now = Timestamp.from(clock.instant())
        val id = existing?.id ?: UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.material_progress(id,revision,created_at,updated_at,updated_by,user_id,material_id,completed,last_access)
            VALUES(?,1,?,?,?,?,?,?,?) ON CONFLICT(user_id,material_id) DO UPDATE SET revision=core.material_progress.revision+1,updated_at=EXCLUDED.updated_at,updated_by=EXCLUDED.updated_by,completed=core.material_progress.completed OR EXCLUDED.completed,last_access=EXCLUDED.last_access""", id, now, now, actorId, actorId, materialId, action == ProgressAction.COMPLETE, now)
        return jdbc.query("SELECT * FROM core.material_progress WHERE user_id=? AND material_id=?", { rs, _ -> progressRow(rs) }, actorId, materialId).first()
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    fun cleanExpiredPreparations() { jdbc.update("DELETE FROM core.files WHERE status='staged' AND expires_at<=?", Timestamp.from(clock.instant())) }

    private fun lockModuleForWrite(moduleId: String, actorId: String, sessionHash: String): LockedModule? {
        val roleNow = requireSession(actorId, sessionHash, "admin", "teacher")
        val classId = jdbc.query("SELECT s.class_id FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=? AND m.withdrawn_at IS NULL", { rs, _ -> rs.getString(1) }, moduleId).firstOrNull() ?: return null
        if (jdbc.query("SELECT id FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, classId).isEmpty()) return null
        if (roleNow == "teacher") jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, actorId)
        val subject = jdbc.query("SELECT s.id,s.class_id FROM core.subjects s WHERE s.id=(SELECT subject_id FROM core.modules WHERE id=?) FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getString(2) }, moduleId).firstOrNull() ?: return null
        val module = jdbc.query("SELECT revision FROM core.modules WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> rs.getInt(1) }, moduleId).firstOrNull() ?: return null
        requireSession(actorId, sessionHash, "admin", "teacher")
        val allowed = roleNow == "admin" && jdbc.queryForObject("SELECT active FROM core.classes WHERE id=?", Boolean::class.java, classId) == true || roleNow == "teacher" && canWriteSubject(subject.first, actorId)
        if (!allowed) throw MaterialsForbidden()
        return LockedModule(subject.first, module)
    }

    private fun canReadSubject(subjectId: String, actorId: String, role: String): Boolean {
        val query = "SELECT EXISTS(SELECT 1 FROM core.subjects s WHERE s.id=? AND ${subjectAccess("s", role)})"
        return jdbc.queryForObject(query, Boolean::class.java, *(listOf(subjectId) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()) == true
    }
    private fun canWriteForum(subjectId: String, actorId: String, role: String): Boolean = when (role) {
        "admin" -> jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id WHERE s.id=? AND c.active)", Boolean::class.java, subjectId) == true
        "teacher" -> jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id JOIN core.subject_teachers st ON st.subject_id=s.id JOIN core.users u ON u.id=st.teacher_id WHERE s.id=? AND c.active AND u.id=? AND u.active AND u.role='teacher')", Boolean::class.java, subjectId, actorId) == true
        "student" -> jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id JOIN core.enrollments e ON e.class_id=c.id JOIN core.users u ON u.id=e.student_id WHERE s.id=? AND c.active AND e.student_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL)", Boolean::class.java, subjectId, actorId) == true
        else -> false
    }
    private fun canWriteSubject(subjectId: String, actorId: String) = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id WHERE s.id=? AND c.active AND (EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND u.active AND u.role='teacher')))", Boolean::class.java, subjectId, actorId) == true
    private fun subjectAccess(subject: String, role: String) = when (role) {
        "admin" -> "true"
        "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=$subject.id AND st.teacher_id=? AND u.active AND u.role='teacher')"
        "student" -> "EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=$subject.class_id AND e.status='active' AND e.student_id=? AND u.active AND u.email_confirmed_at IS NOT NULL)"
        else -> "false"
    }
    private fun canUseStaged(file: FileMetadata, actorId: String, role: String): Boolean = when (file.purpose) {
        FilePurpose.MATERIAL -> role in setOf("teacher", "admin") && canWriteModule(file.parentId, actorId, role)
        FilePurpose.WORK -> canPrepareWork(file.parentId, actorId, role)
        FilePurpose.FORUM -> canWriteForum(file.parentId, actorId, role)
        FilePurpose.QUESTION, FilePurpose.ASSESSMENT -> false
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
        val workAccess = when (role) {
            "admin" -> "true"
            "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users tu ON tu.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND tu.active AND tu.role='teacher')"
            "student" -> "x.user_id=f.owner_id AND x.user_id=? AND a.status='published' AND c.active AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=c.id AND e.student_id=x.user_id AND e.status='active' AND u.active AND u.email_confirmed_at IS NOT NULL AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id))"
            else -> "false"
        }
        val forumAccess = when (role) {
            "admin" -> "true"
            "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=t.subject_id AND st.teacher_id=? AND u.active AND u.role='teacher')"
            "student" -> "c.active AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=c.id AND e.student_id=? AND e.status='active' AND u.active AND u.email_confirmed_at IS NOT NULL)"
            else -> "false"
        }
        val args = listOf(id) + (if (role == "teacher" || role == "student") listOf(actorId) else emptyList()) + (if (role == "teacher" || role == "student") listOf(actorId) else emptyList()) + (if (role == "teacher" || role == "student") listOf(actorId) else emptyList()) + (if (role == "teacher" || role == "student") listOf(actorId) else emptyList())
        return jdbc.queryForObject("""SELECT EXISTS(
            SELECT 1 FROM core.files f WHERE f.id=? AND f.status='attached' AND (
                f.purpose='question'
                OR (f.purpose='assessment' AND EXISTS(
                    SELECT 1 FROM core.assessments a JOIN core.modules m ON m.id=a.module_id AND m.withdrawn_at IS NULL
                    JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
                    WHERE a.id=f.parent_id AND $assessmentAccess
                ))
                OR (f.purpose='work' AND EXISTS(
                    SELECT 1 FROM core.submissions x JOIN core.assessments a ON a.id=x.assessment_id JOIN core.modules m ON m.id=a.module_id AND m.withdrawn_at IS NULL
                    JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
                    WHERE x.assessment_id=f.parent_id AND x.submitted_at IS NOT NULL AND x.attachments @> jsonb_build_array(jsonb_build_object('kind','file','fileId',f.id)) AND $workAccess
                ))
                OR EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id WHERE mt.file_id=f.id AND ${subjectAccess("s", role)})
                OR EXISTS(SELECT 1 FROM core.topics t JOIN core.classes c ON c.id=t.class_id WHERE t.attachment_id=f.id AND t.withdrawn_at IS NULL AND $forumAccess)
            ))""", Boolean::class.java, *args.toTypedArray()) == true
    }

    private fun fileRow(rs: ResultSet): StoredFile {
        val metadata = FileMetadata(
            rs.getString("id"), rs.getString("name"), rs.getLong("size"), rs.getString("mime"),
            storedPurpose(rs.getString("purpose")), rs.getString("parent_id"), storedStatus(rs.getString("status")),
        )
        val preparation = when (metadata.status) {
            FileStatus.STAGED -> FilePreparation(rs.getString("owner_id"), rs.getTimestamp("expires_at").toInstant())
            FileStatus.ATTACHED -> null
        }
        return StoredFile(metadata, rs.getString("sha256"), preparation)
    }

    private fun materialRow(rs: ResultSet): Material {
        val fileId = rs.getString("file_id")
        val file = fileId?.let {
            FileMetadata(
                rs.getString("file_meta_id"), rs.getString("file_name"), rs.getLong("file_size"), rs.getString("file_mime"),
                storedPurpose(rs.getString("file_purpose")), rs.getString("file_parent_id"), storedStatus(rs.getString("file_status")),
            )
        }
        return Material(
            rs.getString("id"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant().toString(),
            rs.getTimestamp("updated_at").toInstant().toString(), rs.getString("updated_by"), rs.getString("module_id"),
            rs.getString("title"), storedKind(rs.getString("kind")), rs.getBigDecimal("duration").toDouble(),
            rs.getString("content"), fileId, rs.getString("video_url"), file,
            NamedPerson(rs.getString("author_id"), rs.getString("author_name")), rs.getInt("order_index"),
        )
    }

    private fun subjectSummary(rs: ResultSet, actorId: String, role: String): MaterialSubject {
        val id = rs.getString("subject_id")
        val teachers = jdbc.query("SELECT u.id,u.name FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=? ORDER BY u.id", { r, _ -> NamedPerson(r.getString(1), r.getString(2)) }, id)
        val publications = jdbc.query("SELECT publication_id FROM core.subject_publications WHERE subject_id=? ORDER BY publication_id", { r, _ -> r.getString(1) }, id)
        val seen = jdbc.query("SELECT jsonb_array_elements_text(publication_ids) FROM core.subject_visits WHERE subject_id=? AND user_id=?", { r, _ -> r.getString(1) }, id, actorId)
        return MaterialSubject(
            id, rs.getInt("subject_revision"), rs.getTimestamp("subject_created").toInstant().toString(),
            rs.getTimestamp("subject_updated").toInstant().toString(), rs.getString("subject_updated_by"),
            rs.getString("class_id"), rs.getString("subject_name"), rs.getString("color"), rs.getString("icon"),
            rs.getString("subject_description"), rs.getString("class_name"), teachers,
            rs.getBoolean("class_active") && (role == "admin" || role == "teacher" && teachers.any { it.id == actorId }),
            (publications - seen.toSet()).isNotEmpty(), publications,
        )
    }

    private fun progressRow(rs: ResultSet) = LearningProgress(
        rs.getString("id"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString(), rs.getString("updated_by"), rs.getString("user_id"),
        rs.getString("material_id"), rs.getBoolean("completed"), rs.getTimestamp("last_access").toInstant().toString(),
    )

    private fun storedKind(value: String) = checkNotNull(MaterialKind.parse(value)) { "Unknown stored material kind" }
    private fun storedPurpose(value: String) = FilePurpose.entries.first { it.value == value }
    private fun storedStatus(value: String) = FileStatus.entries.first { it.value == value }

    private data class LockedModule(val subjectId: String, val revision: Int)

    private data class StoredFile(val metadata: FileMetadata, val sha256: String, val preparation: FilePreparation?) {
        fun questionImageIdentity(): QuestionImageIdentity? = if (metadata.purpose == FilePurpose.QUESTION) {
            QuestionImageIdentity(metadata.parentId, metadata.name, metadata.mime, sha256)
        } else {
            null
        }
    }

    private fun audit(actorId: String, type: String, targetId: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), type, targetId, actorId) }
    private fun requireSession(actorId: String, hash: String, vararg roles: String): String {
        val accepted = if (roles.isEmpty()) listOf("admin", "teacher", "student") else roles.toList()
        val placeholders = accepted.joinToString(",") { "?" }
        return jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, *(listOf(actorId) + accepted + listOf(hash, Timestamp.from(clock.instant()))).toTypedArray()).firstOrNull() ?: throw MaterialsForbidden()
    }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
