package br.com.ajadigital.core.curriculum.adapters.out.persistence

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.curriculum.domain.exceptions.UnknownPublication
import br.com.ajadigital.core.curriculum.domain.exceptions.InvalidAcademicReference
import br.com.ajadigital.core.academic.application.models.AcademicPage
import br.com.ajadigital.core.curriculum.domain.models.*
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort
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
    override fun authorize(request: AuthorizeSubjectDto) {
        val (actorId, role, sessionHash, subjectId) = request
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, role)
        if (subjectId == null) {
            if (currentRole != "admin") throw AcademicForbidden()
        } else {
            val found = jdbc.query("SELECT class_id FROM core.subjects WHERE id=?", { rs, _ -> rs.getString(1) }, subjectId).firstOrNull() ?: throw br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound()
            lockClasses(listOf(found))
            if (currentRole == "teacher") lockTeachers(listOf(actorId))
            val locked = lockSubject(subjectId) ?: throw br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound()
            if (locked["classId"] != found) throw br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict()
            requireSession(actorId, sessionHash, role)
            requireWriter(subjectId, actorId, currentRole)
        }
    }

    override fun subjects(request: ListSubjectsRecordDto): AcademicPage {
        val (actorId, role, sessionHash, page, size, sort, classId, query) = request
        val predicates = mutableListOf("(${access("s", role)})")
        val args = mutableListOf<Any>().apply { if (role == "teacher" || role == "student") add(actorId) }
        if (classId != null) { predicates += "s.class_id=?"; args += classId }
        if (!query.isNullOrBlank()) { predicates += "s.name ILIKE ?"; args += "%$query%" }
        val where = predicates.joinToString(" AND ")
        val total = jdbc.queryForObject("SELECT count(*) FROM core.subjects s WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val rows = jdbc.query("SELECT s.* FROM core.subjects s WHERE $where ORDER BY s.id${direction(sort)} LIMIT ? OFFSET ?", { rs, _ ->
            mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(), "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "classId" to rs.getString("class_id"), "name" to rs.getString("name"), "color" to rs.getString("color"), "icon" to rs.getString("icon"), "description" to rs.getString("description"))
        }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return AcademicPage(subjectMaps(rows, actorId, role), page, size, total)
    }

    override fun subject(request: GetStoredSubjectDto): Map<String, Any?>? {
        val (id, actorId, role, sessionHash) = request
        return jdbc.query("SELECT s.* FROM core.subjects s WHERE s.id=? AND (${access("s", role)})", { rs, _ -> subjectMap(rs, actorId, role) }, *(listOf(id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull()
    }

    override fun subjectModules(request: ListStoredSubjectModulesDto): List<Map<String, Any?>> {
        val (subjectId, actorId, role, sessionHash) = request
        val args = listOf(subjectId) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()
        return jdbc.query("SELECT m.* FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE s.id=? AND m.withdrawn_at IS NULL AND (${access("s", role)}) ORDER BY m.order_index,m.id", { rs, _ -> moduleMap(rs) }, *args.toTypedArray())
    }

    @Transactional
    override fun createSubject(request: CreateSubjectRecordDto): Map<String, Any?> {
        val (actorId, sessionHash, classId, name, teacherIds, color, icon, description) = request
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "admin")
        val activeClass = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: throw br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound()
        if (!activeClass) throw br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict()
        validateTeachers(teacherIds)
        requireSession(actorId, sessionHash, "admin")
        val now = Timestamp.from(clock.instant()); val id = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO core.subjects(id,revision,created_at,updated_at,updated_by,class_id,name,color,icon,description) VALUES(?,1,?,?,?,?,?,?,?,?)", id, now, now, actorId, classId, name, color, icon, description)
        teacherIds.forEach { jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) VALUES(?,?)", id, it) }
        audit(actorId, "academic.subject_created", id)
        return subject(GetStoredSubjectDto(id, actorId, "admin", sessionHash))!!
    }

    @Transactional
    override fun updateSubject(request: UpdateSubjectRecordDto): Map<String, Any?>? {
        val (id, actorId, sessionHash, expectedRevision, classId, name, teacherIds, color, icon, description) = request
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "admin")
        val prior = jdbc.query("SELECT class_id FROM core.subjects WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return null
        val priorTeachers = jdbc.query("SELECT teacher_id FROM core.subject_teachers WHERE subject_id=? ORDER BY teacher_id", { rs, _ -> rs.getString(1) }, id)
        lockClasses(listOfNotNull(prior, classId).distinct())
        lockTeachers(priorTeachers + teacherIds.orEmpty())
        val current = jdbc.query("SELECT * FROM core.subjects WHERE id=? FOR UPDATE", { rs, _ -> subjectMap(rs, actorId, "admin") }, id).firstOrNull() ?: return null
        if (current["revision"] != expectedRevision) return null
        if (current["classId"] != prior || current["teacherIds"] != priorTeachers) return null
        requireSession(actorId, sessionHash, "admin")
        if (classId != null) {
            val activeClass = jdbc.query("SELECT active FROM core.classes WHERE id=?", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: throw br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound()
            if (!activeClass) throw br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict()
        }
        teacherIds?.let(::validateLockedTeachers)
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.subjects SET class_id=?,name=?,color=?,icon=?,description=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", classId ?: current["classId"], name ?: current["name"], color ?: current["color"], icon ?: current["icon"], description ?: current["description"], now, actorId, id, expectedRevision) != 1) return null
        if (teacherIds != null) {
            jdbc.update("DELETE FROM core.subject_teachers WHERE subject_id=?", id)
            teacherIds.forEach { jdbc.update("INSERT INTO core.subject_teachers(subject_id,teacher_id) VALUES(?,?)", id, it) }
        }
        audit(actorId, "academic.subject_updated", id)
        return subject(GetStoredSubjectDto(id, actorId, "admin", sessionHash))
    }

    override fun modules(request: ListStoredModulesDto): AcademicPage {
        val (actorId, role, sessionHash, page, size, sort, subjectId) = request
        val where = buildList {
            add("m.withdrawn_at IS NULL")
            add("EXISTS(SELECT 1 FROM core.subjects s WHERE s.id=m.subject_id AND (${access("s", role)}))")
            if (subjectId != null) add("m.subject_id=?")
        }.joinToString(" AND ")
        val args = mutableListOf<Any>().apply { if (role == "teacher" || role == "student") add(actorId); subjectId?.let(::add) }
        return page("SELECT m.* FROM core.modules m WHERE $where ORDER BY m.order_index${direction(sort)},m.id", "SELECT count(*) FROM core.modules m WHERE $where", args, page, size, ::moduleMap)
    }

    override fun module(request: GetStoredModuleDto): Map<String, Any?>? {
        val (id, actorId, role, sessionHash) = request
        return jdbc.query("SELECT m.* FROM core.modules m JOIN core.subjects s ON s.id=m.subject_id WHERE m.id=? AND m.withdrawn_at IS NULL AND (${access("s", role)})", { rs, _ -> moduleMap(rs) }, *(listOf(id) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()).firstOrNull()
    }

    @Transactional
    override fun createModule(request: CreateModuleRecordDto): Map<String, Any?> {
        val (actorId, role, sessionHash, subjectId, title, description) = request
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val classId = subjectClass(subjectId) ?: throw br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound()
        lockClasses(listOf(classId))
        if (currentRole == "teacher") lockTeachers(listOf(actorId))
        val locked = lockSubject(subjectId) ?: throw br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound()
        if (locked["classId"] != classId) throw br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict()
        requireSession(actorId, sessionHash, "admin", "teacher")
        requireWriter(subjectId, actorId, currentRole)
        val now = Timestamp.from(clock.instant()); val id = UUID.randomUUID().toString()
        val order = jdbc.queryForObject("SELECT COALESCE(max(order_index)+1,0) FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL", Int::class.java, subjectId)!!
        jdbc.update("INSERT INTO core.modules(id,revision,created_at,updated_at,updated_by,subject_id,title,description,order_index) VALUES(?,1,?,?,?,?,?,?,?)", id, now, now, actorId, subjectId, title, description, order)
        jdbc.update("UPDATE core.subjects SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, subjectId)
        audit(actorId, "academic.module_created", id)
        return module(GetStoredModuleDto(id, actorId, role, sessionHash))!!
    }

    @Transactional
    override fun updateModule(request: UpdateModuleRecordDto): Map<String, Any?>? {
        val (id, actorId, role, sessionHash, expectedRevision, title, description) = request
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val module = jdbc.query("SELECT * FROM core.modules WHERE id=? AND withdrawn_at IS NULL", { rs, _ -> moduleMap(rs) }, id).firstOrNull() ?: return null
        val subjectId = module["subjectId"] as String
        val classId = subjectClass(subjectId) ?: return null
        lockClasses(listOf(classId))
        if (currentRole == "teacher") lockTeachers(listOf(actorId))
        val locked = lockSubject(subjectId) ?: return null
        if (locked["classId"] != classId) return null
        requireSession(actorId, sessionHash, "admin", "teacher")
        requireWriter(module["subjectId"] as String, actorId, currentRole)
        if (module["revision"] != expectedRevision) return null
        if (jdbc.update("UPDATE core.modules SET title=?,description=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=? AND withdrawn_at IS NULL", title ?: module["title"], description ?: module["description"], Timestamp.from(clock.instant()), actorId, id, expectedRevision) != 1) return null
        audit(actorId, "academic.module_updated", id)
        return module(GetStoredModuleDto(id, actorId, role, sessionHash))
    }

    @Transactional
    override fun reorderModules(request: ReorderModulesRecordDto): AcademicPage? {
        val (subjectId, actorId, role, sessionHash, expectedRevision, items, page, size) = request
        lockAuthInvariant()
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher")
        val classId = subjectClass(subjectId) ?: return null
        lockClasses(listOf(classId))
        if (currentRole == "teacher") lockTeachers(listOf(actorId))
        val subject = lockSubject(subjectId) ?: return null
        if (subject["classId"] != classId) return null
        requireSession(actorId, sessionHash, "admin", "teacher")
        requireWriter(subjectId, actorId, currentRole)
        if (subject["revision"] != expectedRevision) return null
        val current = jdbc.query("SELECT id,revision FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY id FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getInt(2) }, subjectId)
        if (items.size != current.size || items.map { it.first }.toSet() != current.map { it.first }.toSet() || items.any { item -> current.none { it.first == item.first && it.second == item.second } }) return null
        val now = Timestamp.from(clock.instant())
        items.forEachIndexed { index, item -> jdbc.update("UPDATE core.modules SET order_index=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", index, now, actorId, item.first, item.second) }
        jdbc.update("UPDATE core.subjects SET revision=revision+1,updated_at=?,updated_by=? WHERE id=?", now, actorId, subjectId)
        audit(actorId, "academic.modules_reordered", subjectId)
        val ordered = jdbc.query("SELECT * FROM core.modules WHERE subject_id=? AND withdrawn_at IS NULL ORDER BY order_index,id", { rs, _ -> moduleMap(rs) }, subjectId)
        return AcademicPage(ordered.drop((page - 1) * size).take(size), page, size, ordered.size)
    }

    @Transactional
    override fun visit(request: VisitSubjectRecordDto): Map<String, Any?>? {
        val (subjectId, actorId, role, sessionHash, publicationIds) = request
        lockAuthInvariant()
        requireSession(actorId, sessionHash, "admin", "teacher", "student")
        lockSubject(subjectId) ?: return null
        requireSession(actorId, sessionHash, "admin", "teacher", "student")
        if (subject(GetStoredSubjectDto(subjectId, actorId, role, sessionHash)) == null) return null
        val existing = jdbc.query("SELECT seen_at,publication_ids FROM core.subject_visits WHERE subject_id=? AND user_id=? FOR UPDATE", { rs, _ -> rs.getTimestamp(1)?.toInstant() to mapper.readValue(rs.getString(2), List::class.java).map { it as String } }, subjectId, actorId).firstOrNull()
        val seen = (existing?.second.orEmpty() + publicationIds.orEmpty()).distinct().sorted()
        val published = publications(subjectId)
        if (publicationIds != null && !published.containsAll(publicationIds)) throw UnknownPublication()
        if (publicationIds == null) return visitMap(subjectId, existing?.first, seen, published)
        val now = Timestamp.from(clock.instant())
        if (existing == null) jdbc.update("INSERT INTO core.subject_visits(subject_id,user_id,seen_at,publication_ids) VALUES(?,?,?,?::jsonb)", subjectId, actorId, now, mapper.writeValueAsString(seen))
        else jdbc.update("UPDATE core.subject_visits SET seen_at=?,publication_ids=?::jsonb WHERE subject_id=? AND user_id=?", now, mapper.writeValueAsString(seen), subjectId, actorId)
        return visitMap(subjectId, now.toInstant(), seen, published)
    }

    private fun subjectMap(rs: ResultSet, actorId: String, role: String): Map<String, Any?> {
        val id = rs.getString("id")
        val teachers = jdbc.query("SELECT u.id,u.name FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=? ORDER BY u.id", { r, _ -> mapOf("id" to r.getString(1), "name" to r.getString(2)) }, id)
        val className = jdbc.queryForObject("SELECT name FROM core.classes WHERE id=?", String::class.java, rs.getString("class_id"))
        val publications = publications(id)
        val visited = jdbc.query("SELECT publication_ids FROM core.subject_visits WHERE subject_id=? AND user_id=?", { r, _ -> mapper.readValue(r.getString(1), List::class.java).map { it as String } }, id, actorId).firstOrNull().orEmpty()
        val classActive = jdbc.queryForObject("SELECT active FROM core.classes WHERE id=?", Boolean::class.java, rs.getString("class_id")) == true
        return mapOf("id" to id, "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(), "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "classId" to rs.getString("class_id"), "className" to className, "name" to rs.getString("name"), "teacherIds" to teachers.map { it["id"] }, "teachers" to teachers, "color" to rs.getString("color"), "icon" to rs.getString("icon"), "description" to rs.getString("description"), "canManageContent" to (classActive && (role == "admin" || teachers.any { it["id"] == actorId })), "publicationIds" to publications, "hasUpdates" to (publications - visited.toSet()).isNotEmpty())
    }

    private fun subjectMaps(rows: List<Map<String, Any?>>, actorId: String, role: String): List<Map<String, Any?>> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.getValue("id") as String }
        val placeholders = ids.joinToString(",") { "?" }
        val teachers = jdbc.query("SELECT st.subject_id,u.id,u.name FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id IN ($placeholders) ORDER BY st.subject_id,u.id", { rs, _ -> Triple(rs.getString(1), rs.getString(2), rs.getString(3)) }, *ids.toTypedArray()).groupBy({ it.first }, { mapOf("id" to it.second, "name" to it.third) })
        val classStates = jdbc.query("SELECT id,name,active FROM core.classes WHERE id IN (${rows.map { it.getValue("classId") as String }.distinct().joinToString(",") { "?" }})", { rs, _ -> rs.getString(1) to (rs.getString(2) to rs.getBoolean(3)) }, *rows.map { it.getValue("classId") as String }.distinct().toTypedArray()).toMap()
        val publicationsBySubject = jdbc.query("SELECT subject_id,publication_id FROM core.subject_publications WHERE subject_id IN ($placeholders) ORDER BY subject_id,publication_id", { rs, _ -> rs.getString(1) to rs.getString(2) }, *ids.toTypedArray()).groupBy({ it.first }, { it.second })
        val visitsBySubject = jdbc.query("SELECT subject_id,publication_ids FROM core.subject_visits WHERE user_id=? AND subject_id IN ($placeholders)", { rs, _ -> rs.getString(1) to mapper.readValue(rs.getString(2), List::class.java).map { it as String } }, *(listOf(actorId) + ids).toTypedArray()).toMap()
        return rows.map { row ->
            val id = row.getValue("id") as String
            val assigned = teachers[id].orEmpty()
            val publications = publicationsBySubject[id].orEmpty()
            val visited = visitsBySubject[id].orEmpty()
            val classState = classStates[row.getValue("classId") as String]
            row + mapOf("className" to classState?.first, "teacherIds" to assigned.map { it["id"] }, "teachers" to assigned, "canManageContent" to (classState?.second == true && (role == "admin" || assigned.any { it["id"] == actorId })), "publicationIds" to publications, "hasUpdates" to (publications - visited.toSet()).isNotEmpty())
        }
    }

    private fun moduleMap(rs: ResultSet) = mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(), "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "subjectId" to rs.getString("subject_id"), "title" to rs.getString("title"), "description" to rs.getString("description"), "order" to rs.getInt("order_index"))

    private fun visitMap(subjectId: String, seenAt: java.time.Instant?, seen: List<String>, published: List<String>) = mapOf("subjectId" to subjectId, "seenAt" to seenAt?.toString(), "seenPublicationIds" to seen, "publicationIds" to published, "hasUpdates" to (published - seen.toSet()).isNotEmpty())

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
        return found ?: throw AcademicForbidden()
    }

    // Writes take the shared auth invariant gate first, then class rows, sorted teacher IDs, subject and module rows.
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }

    private fun lockSubject(id: String): Map<String, Any?>? = jdbc.query("SELECT * FROM core.subjects WHERE id=? FOR UPDATE", { rs, _ -> mapOf("revision" to rs.getInt("revision"), "classId" to rs.getString("class_id")) }, id).firstOrNull()

    private fun subjectClass(id: String): String? = jdbc.query("SELECT class_id FROM core.subjects WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull()

    private fun lockClasses(ids: List<String>) { ids.distinct().sorted().forEach { id -> if (jdbc.query("SELECT id FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, id).isEmpty()) throw br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound() } }

    private fun lockTeachers(ids: List<String>) { ids.distinct().sorted().forEach { id -> jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, id) } }

    private fun requireWriter(subjectId: String, actorId: String, role: String) {
        val allowed = role == "admin" && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subjects s JOIN core.classes c ON c.id=s.class_id WHERE s.id=? AND c.active)", Boolean::class.java, subjectId) == true || role == "teacher" && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id JOIN core.subjects s ON s.id=st.subject_id JOIN core.classes c ON c.id=s.class_id WHERE st.subject_id=? AND st.teacher_id=? AND u.role='teacher' AND u.active AND c.active)", Boolean::class.java, subjectId, actorId) == true
        if (!allowed) throw AcademicForbidden()
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

    private fun page(select: String, count: String, args: List<Any>, page: Int, size: Int, map: (ResultSet) -> Map<String, Any?>): AcademicPage {
        val total = jdbc.queryForObject(count, Int::class.java, *args.toTypedArray()) ?: 0
        val items = jdbc.query("$select LIMIT ? OFFSET ?", { rs, _ -> map(rs) }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return AcademicPage(items, page, size, total)
    }

    private fun audit(actorId: String, type: String, subjectId: String) {
        jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), type, subjectId, actorId)
    }

    private fun direction(sort: String) = if (sort.startsWith("-")) " DESC" else " ASC"
}
