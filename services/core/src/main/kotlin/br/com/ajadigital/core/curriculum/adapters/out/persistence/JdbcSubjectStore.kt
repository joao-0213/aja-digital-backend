package br.com.ajadigital.core.curriculum.adapters.out.persistence

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary
import br.com.ajadigital.core.curriculum.application.contracts.SubjectTeacher
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.RevisionWrite
import br.com.ajadigital.core.curriculum.application.models.SubjectVisit
import br.com.ajadigital.core.curriculum.application.models.store.StoreAuthorizeSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreListSubjectsQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreModuleQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreModulesQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreReorderModulesCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectModulesQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreVisitSubjectCommand
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumConflict
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumForbidden
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumNotFound
import br.com.ajadigital.core.curriculum.domain.exceptions.InvalidAcademicReference
import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision
import br.com.ajadigital.core.curriculum.domain.policies.ModuleOrdering
import br.com.ajadigital.core.curriculum.domain.policies.SubjectPublications
import br.com.ajadigital.core.shared.application.contracts.Page
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcSubjectStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val clock: Clock) : SubjectStoreOutputPort {
    @Transactional
    override fun authorize(request: StoreAuthorizeSubjectCommand) {
        val actorId = request.actorId
        val role = request.role
        val sessionHash = request.sessionHash
        val subjectId = request.subjectId
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, role)
        if (subjectId == null) {
            if (currentRole != "admin") throw CurriculumForbidden()
        } else {
            val found = jdbc.query("SELECT class_id FROM core.subjects WHERE id=?", { rs, _ -> rs.getString(1) }, subjectId).firstOrNull() ?: throw CurriculumNotFound()
            lockClasses(listOf(found))
            if (currentRole == "teacher") lockTeachers(listOf(actorId))
            val locked = lockSubject(subjectId) ?: throw CurriculumNotFound()
            if (locked.classId != found) throw CurriculumConflict()
            requireSession(actorId, sessionHash, role)
            requireWriter(subjectId, actorId, currentRole)
        }
    }

    override fun subjects(request: StoreListSubjectsQuery): Page<SubjectSummary> {
        val actorId = request.actorId
        val role = request.role
        val page = request.page
        val size = request.size
        val sort = request.sort
        val classId = request.classId
        val query = request.query
        val predicates = mutableListOf("(${access("s", role)})")
        val args = mutableListOf<Any>().apply { if (role == "teacher" || role == "student") add(actorId) }
        if (classId != null) {
            predicates += "s.class_id=?"
            args += classId
        }
        if (!query.isNullOrBlank()) {
            predicates += "s.name ILIKE ?"
            args += "%$query%"
        }
        val where = predicates.joinToString(" AND ")
        val total = jdbc.queryForObject("SELECT count(*) FROM core.subjects s WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.query("SELECT s.* FROM core.subjects s WHERE $where ORDER BY s.id${direction(sort)} LIMIT ? OFFSET ?", { rs, _ ->
            readSubject(rs)
        }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return Page(subjectSummaries(rows, actorId, role), page, size, total)
    }

    override fun subject(request: StoreSubjectQuery): SubjectSummary? {
        val (id, actorId, role) = request
        return jdbc.query("SELECT s.* FROM core.subjects s WHERE s.id=? AND (${access("s", role)})", { rs, _ -> subjectSummary(rs, actorId, role) }, *(listOf(id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull()
    }

    override fun subjectModules(request: StoreSubjectModulesQuery): List<Module> {
        val (subjectId, actorId, role) = request
        val args = listOf(subjectId) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()
        return jdbc.query("SELECT m.* FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE s.id=? AND m.withdrawn_at IS NULL AND (${access("s", role)}) ORDER BY m.order_index,m.id", { rs, _ -> readModule(rs) }, *args.toTypedArray())
    }

    @Transactional
    override fun createSubject(request: StoreCreateSubjectCommand): Subject {
        val actorId = request.actorId
        val sessionHash = request.sessionHash
        val classId = request.classId
        val name = request.name
        val teacherIds = request.teacherIds
        val color = request.color
        val icon = request.icon
        val description = request.description
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "admin")
        val activeClass = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: throw CurriculumNotFound()
        if (!activeClass) throw CurriculumConflict()
        validateTeachers(teacherIds)
        requireSession(actorId, sessionHash, "admin")
        val now = Timestamp.from(clock.instant())
        val id = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO core.subjects(id,revision,created_at,updated_at,updated_by,class_id,name,color,icon,description) VALUES(?,1,?,?,?,?,?,?,?,?)", id, now, now, actorId, classId, name, color, icon, description)
        teacherIds.forEach { jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) VALUES(?,?)", id, it) }
        audit(actorId, "academic.subject_created", id)
        return checkNotNull(subject(StoreSubjectQuery(id, actorId, "admin"))).subject
    }

    @Transactional
    override fun updateSubject(request: StoreUpdateSubjectCommand): RevisionWrite<Subject> {
        val id = request.id
        val actorId = request.actorId
        val sessionHash = request.sessionHash
        val expectedRevision = request.expectedRevision
        val classId = request.classId
        val name = request.name
        val teacherIds = request.teacherIds
        val color = request.color
        val icon = request.icon
        val description = request.description
        fun rejected() = rejectedWrite(subject(StoreSubjectQuery(id, actorId, "admin"))?.subject?.revision)
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "admin")
        val prior = jdbc.query("SELECT class_id FROM core.subjects WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return rejected()
        val priorTeachers = jdbc.query("SELECT teacher_id FROM core.subject_teachers WHERE subject_id=? ORDER BY teacher_id", { rs, _ -> rs.getString(1) }, id)
        lockClasses(listOfNotNull(prior, classId).distinct())
        lockTeachers(priorTeachers + teacherIds.orEmpty())
        val current = jdbc.query("SELECT * FROM core.subjects WHERE id=? FOR UPDATE", { rs, _ -> subjectSummary(rs, actorId, "admin") }, id).firstOrNull()?.subject ?: return rejected()
        if (current.revision != expectedRevision || subjectAudienceChanged(current, prior, priorTeachers)) return rejected()
        requireSession(actorId, sessionHash, "admin")
        classId?.let(::requireActiveTargetClass)
        teacherIds?.let(::validateLockedTeachers)
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.subjects SET class_id=?,name=?,color=?,icon=?,description=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", classId ?: current.classId, name ?: current.name, color ?: current.color, icon ?: current.icon, description ?: current.description, now, actorId, id, expectedRevision) != 1) return rejected()
        if (teacherIds != null) {
            jdbc.update("DELETE FROM core.subject_teachers WHERE subject_id=?", id)
            teacherIds.forEach { jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) VALUES(?,?)", id, it) }
        }
        audit(actorId, "academic.subject_updated", id)
        return subject(StoreSubjectQuery(id, actorId, "admin"))?.subject?.let { RevisionWrite.Applied(it) } ?: rejected()
    }

    private fun requireActiveTargetClass(classId: String) {
        val activeClass = jdbc.query("SELECT active FROM core.classes WHERE id=?", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: throw CurriculumNotFound()
        if (!activeClass) throw CurriculumConflict()
    }

    private fun subjectAudienceChanged(current: Subject, priorClass: String, priorTeachers: List<String>): Boolean = current.classId != priorClass || current.teacherIds != priorTeachers

    override fun modules(request: StoreModulesQuery): Page<Module> {
        val actorId = request.actorId
        val role = request.role
        val page = request.page
        val size = request.size
        val sort = request.sort
        val subjectId = request.subjectId
        val where = buildList {
            add("m.withdrawn_at IS NULL")
            add("EXISTS(SELECT 1 FROM core.subjects s WHERE s.id=m.subject_id AND (${access("s", role)}))")
            if (subjectId != null) add("m.subject_id=?")
        }.joinToString(" AND ")
        val args = mutableListOf<Any>().apply {
            if (role == "teacher" || role == "student") add(actorId)
            subjectId?.let(::add)
        }
        return page("SELECT m.* FROM core.modules m WHERE $where ORDER BY m.order_index${direction(sort)},m.id", "SELECT count(*) FROM core.modules m WHERE $where", args, page, size, ::readModule)
    }

    override fun module(request: StoreModuleQuery): Module? {
        val (id, actorId, role) = request
        return jdbc.query("SELECT m.* FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=? AND m.withdrawn_at IS NULL AND (${access("s", role)})", { rs, _ -> readModule(rs) }, *(listOf(id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull()
    }

    @Transactional
    override fun createModule(request: StoreCreateModuleCommand): Module {
        val actorId = request.actorId
        val role = request.role
        val sessionHash = request.sessionHash
        val subjectId = request.subjectId
        val title = request.title
        val description = request.description
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val classId = subjectClass(subjectId) ?: throw CurriculumNotFound()
        lockClasses(listOf(classId))
        if (currentRole == "teacher") lockTeachers(listOf(actorId))
        val locked = lockSubject(subjectId) ?: throw CurriculumNotFound()
        if (locked.classId != classId) throw CurriculumConflict()
        requireSession(actorId, sessionHash, "admin", "teacher")
        requireWriter(subjectId, actorId, currentRole)
        val now = Timestamp.from(clock.instant())
        val id = UUID.randomUUID().toString()
        val order = jdbc.queryForObject("SELECT COALESCE(max(order_index)+1,0) FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL", Int::class.java, subjectId)!!
        jdbc.update("INSERT INTO core.modules(id,revision,created_at,updated_at,updated_by,subject_id,title,description,order_index) VALUES(?,1,?,?,?,?,?,?,?)", id, now, now, actorId, subjectId, title, description, order)
        jdbc.update("UPDATE core.subjects SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, subjectId)
        audit(actorId, "academic.module_created", id)
        return checkNotNull(module(StoreModuleQuery(id, actorId, role)))
    }

    @Transactional
    override fun updateModule(request: StoreUpdateModuleCommand): RevisionWrite<Module> {
        val id = request.id
        val actorId = request.actorId
        val role = request.role
        val sessionHash = request.sessionHash
        val expectedRevision = request.expectedRevision
        val title = request.title
        val description = request.description
        fun rejected() = rejectedWrite(module(StoreModuleQuery(id, actorId, role))?.revision)
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val module = jdbc.query("SELECT * FROM core.modules WHERE id=? AND withdrawn_at IS NULL", { rs, _ -> readModule(rs) }, id).firstOrNull() ?: return rejected()
        val subjectId = module.subjectId
        val classId = subjectClass(subjectId) ?: return rejected()
        lockClasses(listOf(classId))
        if (currentRole == "teacher") lockTeachers(listOf(actorId))
        lockSubject(subjectId)?.takeIf { it.classId == classId } ?: return rejected()
        requireSession(actorId, sessionHash, "admin", "teacher")
        requireWriter(module.subjectId, actorId, currentRole)
        if (module.revision != expectedRevision) return rejected()
        if (jdbc.update("UPDATE core.modules SET title=?,description=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND withdrawn_at IS NULL", title ?: module.title, description ?: module.description, Timestamp.from(clock.instant()), actorId, id, expectedRevision) != 1) return rejected()
        audit(actorId, "academic.module_updated", id)
        return module(StoreModuleQuery(id, actorId, role))?.let { RevisionWrite.Applied(it) } ?: rejected()
    }

    @Transactional
    override fun reorderModules(request: StoreReorderModulesCommand): RevisionWrite<Page<Module>> {
        val subjectId = request.subjectId
        val actorId = request.actorId
        val role = request.role
        val sessionHash = request.sessionHash
        val expectedRevision = request.expectedRevision
        val items = request.items
        val page = request.page
        val size = request.size
        fun rejected() = rejectedWrite(subject(StoreSubjectQuery(subjectId, actorId, role))?.subject?.revision)
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val classId = subjectClass(subjectId) ?: return rejected()
        lockClasses(listOf(classId))
        if (currentRole == "teacher") lockTeachers(listOf(actorId))
        val subject = lockSubject(subjectId) ?: return rejected()
        if (subject.classId != classId) return rejected()
        requireSession(actorId, sessionHash, "admin", "teacher")
        requireWriter(subjectId, actorId, currentRole)
        if (subject.revision != expectedRevision) return rejected()
        val current = jdbc.query("SELECT id,revision FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY id FOR UPDATE", { rs, _ -> ModuleRevision(rs.getString(1), rs.getInt(2)) }, subjectId)
        if (!ModuleOrdering.accepts(items, current)) return rejected()
        val now = Timestamp.from(clock.instant())
        items.forEachIndexed { index, item -> jdbc.update("UPDATE core.modules SET order_index=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", index, now, actorId, item.id, item.revision) }
        jdbc.update("UPDATE core.subjects SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, subjectId)
        audit(actorId, "academic.modules_reordered", subjectId)
        val ordered = jdbc.query("SELECT * FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY order_index,id", { rs, _ -> readModule(rs) }, subjectId)
        return RevisionWrite.Applied(Page(ordered.drop((page - 1) * size).take(size), page, size, ordered.size))
    }

    @Transactional
    override fun visit(request: StoreVisitSubjectCommand): SubjectVisit? {
        val subjectId = request.subjectId
        val actorId = request.actorId
        val role = request.role
        val sessionHash = request.sessionHash
        val publicationIds = request.publicationIds
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "admin", "teacher", "student")
        lockSubject(subjectId) ?: return null
        requireSession(actorId, sessionHash, "admin", "teacher", "student")
        if (subject(StoreSubjectQuery(subjectId, actorId, role)) == null) return null
        val existing = jdbc.query("SELECT seen_at,publication_ids FROM core.subject_visits WHERE subject_id=? AND user_id=? FOR UPDATE", { rs, _ -> rs.getTimestamp(1)?.toInstant() to readPublicationIds(rs.getString(2)) }, subjectId, actorId).firstOrNull()
        val published = publications(subjectId)
        val seen = SubjectPublications.seenAfter(existing?.second.orEmpty(), publicationIds, published)
        if (publicationIds == null) return visitResult(subjectId, existing?.first, seen, published)
        val now = Timestamp.from(clock.instant())
        if (existing == null) {
            jdbc.update("INSERT INTO core.subject_visits(subject_id,user_id,seen_at,publication_ids) VALUES(?,?,?,?::jsonb)", subjectId, actorId, now, mapper.writeValueAsString(seen))
        } else {
            jdbc.update("UPDATE core.subject_visits SET seen_at=?,publication_ids=?::jsonb WHERE subject_id=? AND user_id=?", now, mapper.writeValueAsString(seen), subjectId, actorId)
        }
        return visitResult(subjectId, now.toInstant(), seen, published)
    }

    private fun readSubject(rs: ResultSet) = Subject(
        id = rs.getString("id"),
        revision = rs.getInt("revision"),
        createdAt = rs.getTimestamp("created_at").toInstant().toString(),
        updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
        updatedBy = rs.getString("updated_by"),
        classId = rs.getString("class_id"),
        name = rs.getString("name"),
        teacherIds = emptyList(),
        color = rs.getString("color"),
        icon = rs.getString("icon"),
        description = rs.getString("description"),
    )

    private fun subjectSummary(rs: ResultSet, actorId: String, role: String): SubjectSummary {
        val subject = readSubject(rs)
        val teachers = jdbc.query("SELECT u.id,u.name FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=? ORDER BY u.id", { r, _ -> SubjectTeacher(r.getString(1), r.getString(2)) }, subject.id)
        val className = jdbc.queryForObject("SELECT name FROM core.classes WHERE id=?", String::class.java, subject.classId)
        val publications = publications(subject.id)
        val visited = jdbc.query("SELECT publication_ids FROM core.subject_visits WHERE subject_id=? AND user_id=?", { r, _ -> readPublicationIds(r.getString(1)) }, subject.id, actorId).firstOrNull().orEmpty()
        val classActive = jdbc.queryForObject("SELECT active FROM core.classes WHERE id=?", Boolean::class.java, subject.classId) == true
        return summary(subject, className, classActive, teachers, publications, visited, actorId, role)
    }

    private fun subjectSummaries(rows: List<Subject>, actorId: String, role: String): List<SubjectSummary> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map(Subject::id)
        val placeholders = ids.joinToString(",") { "?" }
        val teachers = jdbc.query("SELECT st.subject_id,u.id,u.name FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id IN ($placeholders) ORDER BY st.subject_id,u.id", { rs, _ -> rs.getString(1) to SubjectTeacher(rs.getString(2), rs.getString(3)) }, *ids.toTypedArray()).groupBy({ it.first }, { it.second })
        val classIds = rows.map(Subject::classId).distinct()
        val classPlaceholders = classIds.joinToString(",") { "?" }
        val classStates = jdbc.query("SELECT id,name,active FROM core.classes WHERE id IN ($classPlaceholders)", { rs, _ -> rs.getString(1) to (rs.getString(2) to rs.getBoolean(3)) }, *classIds.toTypedArray()).toMap()
        val publicationsBySubject = jdbc.query("SELECT subject_id,publication_id FROM core.subject_publications WHERE subject_id IN ($placeholders) ORDER BY subject_id,publication_id", { rs, _ -> rs.getString(1) to rs.getString(2) }, *ids.toTypedArray()).groupBy({ it.first }, { it.second })
        val visitsBySubject = jdbc.query("SELECT subject_id,publication_ids FROM core.subject_visits WHERE user_id=? AND subject_id IN ($placeholders)", { rs, _ -> rs.getString(1) to readPublicationIds(rs.getString(2)) }, *(listOf(actorId) + ids).toTypedArray()).toMap()
        return rows.map { row ->
            val classState = classStates[row.classId]
            summary(
                row,
                classState?.first,
                classState?.second == true,
                teachers[row.id].orEmpty(),
                publicationsBySubject[row.id].orEmpty(),
                visitsBySubject[row.id].orEmpty(),
                actorId,
                role,
            )
        }
    }

    private fun summary(
        subject: Subject,
        className: String?,
        classActive: Boolean,
        teachers: List<SubjectTeacher>,
        publications: List<String>,
        visited: List<String>,
        actorId: String,
        role: String,
    ) = SubjectSummary(
        subject.copy(teacherIds = teachers.map(SubjectTeacher::id)),
        className,
        teachers,
        classActive && (role == "admin" || teachers.any { it.id == actorId }),
        publications,
        SubjectPublications.hasUpdates(publications, visited),
    )

    private fun readModule(rs: ResultSet) = Module(
        id = rs.getString("id"),
        revision = rs.getInt("revision"),
        createdAt = rs.getTimestamp("created_at").toInstant().toString(),
        updatedAt = rs.getTimestamp("updated_at").toInstant().toString(),
        updatedBy = rs.getString("updated_by"),
        subjectId = rs.getString("subject_id"),
        title = rs.getString("title"),
        description = rs.getString("description"),
        order = rs.getInt("order_index"),
    )

    private fun readPublicationIds(json: String): List<String> {
        val publications = mapper.readTree(json)
        check(publications.isArray)
        return (0 until publications.size()).map { index ->
            val id = publications.get(index)
            check(id.isTextual)
            id.asString()
        }
    }

    private fun visitResult(subjectId: String, seenAt: java.time.Instant?, seen: List<String>, published: List<String>) = SubjectVisit(subjectId, seenAt?.toString(), seen, published, SubjectPublications.hasUpdates(published, seen))

    private fun rejectedWrite(currentRevision: Int?): RevisionWrite<Nothing> = currentRevision?.let { RevisionWrite.Conflict(it) } ?: RevisionWrite.Missing

    private fun publications(subjectId: String) = jdbc.query("SELECT publication_id FROM core.subject_publications WHERE subject_id=? ORDER BY publication_id", { rs, _ -> rs.getString(1) }, subjectId)

    private fun access(subject: String, role: String) = when (role) {
        "admin" -> "true"
        "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=$subject.id AND st.teacher_id=? AND u.active AND u.role='teacher')"
        "student" -> "EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=$subject.class_id AND e.status='active' AND e.student_id=? AND u.active AND u.email_confirmed_at IS NOT NULL)"
        else -> "false"
    }

    private fun requireSession(actorId: String, hash: String, vararg roles: String): String {
        require(roles.isNotEmpty())
        val placeholders = roles.joinToString(",") { "?" }
        val args = listOf(actorId) + roles + listOf(hash, Timestamp.from(clock.instant()))
        val found = jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($placeholders) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, *args.toTypedArray()).firstOrNull()
        return found ?: throw CurriculumForbidden()
    }

    // Writes take the shared auth invariant gate first, then class rows, sorted teacher IDs, subject and module rows.
    private fun lockAuthInvariant() {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java)
    }

    private data class LockedSubject(val revision: Int, val classId: String)

    private fun lockSubject(id: String): LockedSubject? = jdbc.query("SELECT * FROM core.subjects WHERE id=? FOR UPDATE", { rs, _ -> LockedSubject(rs.getInt("revision"), rs.getString("class_id")) }, id).firstOrNull()

    private fun subjectClass(id: String): String? = jdbc.query("SELECT class_id FROM core.subjects WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull()

    private fun lockClasses(ids: List<String>) {
        ids.distinct().sorted().forEach { id -> if (jdbc.query("SELECT id FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, id).isEmpty()) throw CurriculumNotFound() }
    }

    private fun lockTeachers(ids: List<String>) {
        ids.distinct().sorted().forEach { id -> jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, id) }
    }

    private fun requireWriter(subjectId: String, actorId: String, role: String) {
        val allowed = role == "admin" && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id WHERE s.id=? AND c.active)", Boolean::class.java, subjectId) == true || role == "teacher" && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id JOIN core.subjects s ON s.id=st.subject_id JOIN core.classes c ON c.id=s.class_id WHERE st.subject_id=? AND st.teacher_id=? AND u.role='teacher' AND u.active AND c.active)", Boolean::class.java, subjectId, actorId) == true
        if (!allowed) throw CurriculumForbidden()
    }

    private fun validateTeachers(ids: List<String>) {
        lockTeachers(ids)
        validateLockedTeachers(ids)
    }

    private fun validateLockedTeachers(ids: List<String>) {
        if (ids.isNotEmpty()) {
            val eligible = jdbc.query("SELECT id FROM core.users WHERE id IN (${ids.distinct().sorted().joinToString(",") { "?" }}) AND role='teacher' AND active", { rs, _ -> rs.getString(1) }, *ids.distinct().sorted().toTypedArray())
            if (eligible.size != ids.size) throw InvalidAcademicReference()
        }
    }

    private fun <T> page(select: String, count: String, args: List<Any>, page: Int, size: Int, map: (ResultSet) -> T): Page<T> {
        val total = jdbc.queryForObject(count, Int::class.java, *args.toTypedArray()) ?: 0
        val items = jdbc.query("$select LIMIT ? OFFSET ?", { rs, _ -> map(rs) }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return Page(items, page, size, total)
    }

    private fun audit(actorId: String, type: String, subjectId: String) {
        jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), type, subjectId, actorId)
    }

    private fun direction(sort: String) = if (sort.startsWith("-")) " DESC" else " ASC"
}
