package br.com.ajadigital.core.adapter.out.persistence

import br.com.ajadigital.core.application.academic.AcademicConflict
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.application.academic.InvalidAttendanceRecords
import br.com.ajadigital.core.ports.out.AcademicPage
import br.com.ajadigital.core.ports.out.AttendanceDraft
import br.com.ajadigital.core.ports.out.AttendanceStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.annotation.Isolation
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Repository
class JdbcAttendanceStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val clock: Clock) : AttendanceStore {
    @Transactional
    override fun authorizeCreate(actorId: String, role: String, sessionHash: String, subjectId: String) {
        val classId = subjectClass(subjectId) ?: throw AcademicNotFound()
        requireWriter(actorId, sessionHash, classId, subjectId, activeClass = true, lock = true)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    override fun list(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, subjectId: String?, studentId: String?, from: LocalDate?, to: LocalDate?): AcademicPage {
        val currentRole = requireSession(actorId, sessionHash, "admin", "teacher", "student")
        if (currentRole == "student" && studentId != null && studentId != actorId) return AcademicPage(emptyList(), page, size, 0)
        val historyScope = "(${datedEnrollment("a.class_date")} OR ${legacyEnrollment("a.class_date", "jsonb_exists(a.records,e.student_id)")})"
        val filters = mutableListOf("(?='admin' OR (?='teacher' AND EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users tu ON tu.id=st.teacher_id WHERE st.subject_id=a.subject_id AND st.teacher_id=? AND tu.active AND tu.role='teacher')) OR (?='student' AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users su ON su.id=e.student_id WHERE e.class_id=s.class_id AND e.student_id=? AND e.status IN ('active','ended') AND su.active AND su.email_confirmed_at IS NOT NULL AND $historyScope)))")
        val args = mutableListOf<Any>(currentRole, currentRole, actorId, currentRole, actorId)
        subjectId?.let { filters += "a.subject_id=?"; args += it }
        if (studentId != null) { filters += "((?='student' AND ?=?) OR (?<>'student' AND EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users su ON su.id=e.student_id WHERE e.class_id=s.class_id AND e.student_id=? AND e.status IN ('active','ended') AND su.active AND su.email_confirmed_at IS NOT NULL AND $historyScope)))"; args += currentRole; args += studentId; args += actorId; args += currentRole; args += studentId }
        from?.let { filters += "a.class_date>=?"; args += it }
        to?.let { filters += "a.class_date<=?"; args += it }
        val where = filters.joinToString(" AND ")
        val total = jdbc.queryForObject("SELECT count(*) FROM core.attendance a JOIN core.subjects s ON s.id=a.subject_id WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val direction = if (sort == "-date") "DESC" else "ASC"
        val rows = jdbc.query("SELECT a.*,s.class_id FROM core.attendance a JOIN core.subjects s ON s.id=a.subject_id WHERE $where ORDER BY a.class_date $direction,a.id LIMIT ? OFFSET ?", { rs, _ -> row(rs) }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return AcademicPage(projectPage(rows, actorId, currentRole, sessionHash, studentId), page, size, total)
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    override fun eligible(actorId: String, role: String, sessionHash: String, subjectId: String, date: LocalDate, time: LocalTime, page: Int, size: Int): AcademicPage {
        requireSession(actorId, sessionHash, "admin", "teacher")
        val classId = subjectClass(subjectId) ?: throw AcademicNotFound()
        requireWriter(actorId, sessionHash, classId, subjectId, activeClass = true)
        val query = """SELECT u.id,u.name,
            bool_or(${datedEnrollment("?")}) eligible,
            bool_or(e.legacy AND e.enrolled_at IS NULL) legacy,
            bool_or(e.enrolled_at IS NOT NULL AND (e.enrolled_at AT TIME ZONE 'America/Sao_Paulo')::date>?) before_enrollment,
            bool_or(e.ended_at IS NOT NULL AND (e.ended_at AT TIME ZONE 'America/Sao_Paulo')::date<?) after_exit
            FROM core.enrollments e JOIN core.users u ON u.id=e.student_id
            WHERE e.class_id=? AND e.status IN ('active','ended') AND u.role='student' AND u.active AND u.email_confirmed_at IS NOT NULL
            GROUP BY u.id,u.name ORDER BY u.id LIMIT ? OFFSET ?"""
        val values = jdbc.query(query, { rs, _ -> mapOf("id" to rs.getString("id"), "name" to rs.getString("name"), "eligible" to rs.getBoolean("eligible"), "reason" to when { !rs.getBoolean("eligible") && rs.getBoolean("legacy") -> "legacy"; !rs.getBoolean("eligible") && rs.getBoolean("before_enrollment") -> "beforeEnrollment"; !rs.getBoolean("eligible") && rs.getBoolean("after_exit") -> "afterExit"; else -> null }) }, date, date, date, date, classId, size, (page - 1) * size)
        val total = jdbc.queryForObject("SELECT count(DISTINCT e.student_id) FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=? AND e.status IN ('active','ended') AND u.role='student' AND u.active AND u.email_confirmed_at IS NOT NULL", Int::class.java, classId) ?: 0
        return AcademicPage(values, page, size, total)
    }

    override fun attendance(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        requireSession(actorId, sessionHash, "admin", "teacher", "student")
        val current = jdbc.query("SELECT a.*,s.class_id FROM core.attendance a JOIN core.subjects s ON s.id=a.subject_id WHERE a.id=?", { rs, _ -> row(rs) }, id).firstOrNull() ?: return null
        requireSession(actorId, sessionHash, "admin", "teacher", "student")
        return project(current, actorId, sessionHash, null)
    }

    @Transactional
    override fun create(actorId: String, role: String, sessionHash: String, draft: AttendanceDraft): Map<String, Any?> {
        val classId = subjectClass(draft.subjectId) ?: throw AcademicNotFound()
        requireWriter(actorId, sessionHash, classId, draft.subjectId, activeClass = true, lock = true)
        val eligible = markableIds(classId, draft.date)
        if (!eligible.containsAll(draft.records.keys)) throw InvalidAttendanceRecords()
        val id = UUID.randomUUID().toString()
        val now = Timestamp.from(clock.instant())
        jdbc.update("INSERT INTO core.attendance(id,revision,created_at,updated_at,updated_by,subject_id,class_date,class_time,records) VALUES(?,1,?,?,?,?,?,?,?::jsonb)", id, now, now, actorId, draft.subjectId, draft.date, draft.time, mapper.writeValueAsString(draft.records))
        audit(actorId, "academic.attendance_created", id)
        return project(staffRow(id)!!, actorId, sessionHash, null)
    }

    @Transactional
    override fun update(actorId: String, role: String, sessionHash: String, id: String, revision: Int, records: Map<String, Boolean>): Map<String, Any?>? {
        val subjectId = jdbc.query("SELECT subject_id FROM core.attendance WHERE id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull() ?: return null
        val classId = subjectClass(subjectId) ?: return null
        requireWriter(actorId, sessionHash, classId, subjectId, activeClass = true, lock = true)
        val current = jdbc.query("SELECT revision,class_date,records FROM core.attendance WHERE id=? FOR UPDATE", { rs, _ -> Triple(rs.getInt(1), rs.getObject(2, LocalDate::class.java), records(rs.getString("records"))) }, id).firstOrNull() ?: return null
        if (current.first != revision) throw AcademicConflict(current.first)
        val markable = markableIds(classId, current.second)
        val immutable = preservedLegacyIds(classId, current.second, current.third)
        if (!markable.containsAll(records.keys - immutable) || records.any { (student, present) -> student in immutable && current.third[student] != present }) throw InvalidAttendanceRecords()
        val nextRecords = current.third.filterKeys { it !in markable } + records
        val now = Timestamp.from(clock.instant())
        jdbc.update("UPDATE core.attendance SET revision=revision+1,updated_at=?,updated_by=?,records=?::jsonb WHERE id=? AND revision=?", now, actorId, mapper.writeValueAsString(nextRecords), id, revision)
        audit(actorId, "academic.attendance_updated", id)
        return staffRow(id)?.let { project(it, actorId, sessionHash, null) }
    }

    private fun projectPage(rows: List<Map<String, Any?>>, actorId: String, role: String, hash: String, studentFilter: String?): List<Map<String, Any?>> {
        if (rows.isEmpty()) return emptyList()
        if (role == "student") return rows.map { studentProjection(it, actorId) }
        val ids = rows.map { it.getValue("id") as String }
        val assignments = if (role == "teacher") assignedSubjects(actorId, rows.map { it.getValue("subjectId") as String }.distinct()) else emptySet()
        val visibleRecords = visibleRecords(ids)
        return rows.filter { role == "admin" || it["subjectId"] in assignments }.map { row ->
            val records = row.getValue("records") as Map<String, Boolean>
            staffProjection(row, records.filterKeys { it in visibleRecords[row.getValue("id")].orEmpty() && (studentFilter == null || it == studentFilter) })
        }
    }

    private fun project(row: Map<String, Any?>, actorId: String, hash: String, studentFilter: String?): Map<String, Any?> {
        val role = requireSession(actorId, hash, "admin", "teacher", "student")
        return if (role == "student") {
            val date = row.getValue("date") as LocalDate
            val records = row.getValue("records") as Map<String, Boolean>
            if (!studentEnrolled(actorId, row.getValue("classId") as String, date, actorId in records)) throw AcademicNotFound()
            studentProjection(row, actorId)
        } else {
            if (role == "teacher" && !assigned(actorId, row.getValue("subjectId") as String)) throw AcademicNotFound()
            val records = row.getValue("records") as Map<String, Boolean>
            val visible = (markableIds(row.getValue("classId") as String, row.getValue("date") as LocalDate) + preservedLegacyIds(row.getValue("classId") as String, row.getValue("date") as LocalDate, records)).toSet()
            staffProjection(row, records.filterKeys { it in visible && (studentFilter == null || it == studentFilter) })
        }
    }

    private fun studentProjection(row: Map<String, Any?>, studentId: String) = mapOf("id" to row["id"], "revision" to row["revision"], "createdAt" to row["createdAt"], "updatedAt" to row["updatedAt"], "updatedBy" to row["updatedBy"], "subjectId" to row["subjectId"], "date" to (row["date"] as LocalDate).toString(), "time" to row["time"].toString().substring(0, 5), "present" to (row["records"] as Map<*, *>)[studentId])

    private fun staffProjection(row: Map<String, Any?>, records: Map<String, Boolean>) = mapOf("id" to row["id"], "revision" to row["revision"], "createdAt" to row["createdAt"], "updatedAt" to row["updatedAt"], "updatedBy" to row["updatedBy"], "subjectId" to row["subjectId"], "date" to (row["date"] as LocalDate).toString(), "time" to row["time"].toString().substring(0, 5), "records" to records)

    private fun visibleRecords(ids: List<String>): Map<String, Set<String>> {
        val placeholders = ids.joinToString(",") { "?" }
        val scope = "(${datedEnrollment("a.class_date")} OR ${legacyEnrollment("a.class_date", "jsonb_exists(a.records,e.student_id)")})"
        return jdbc.query("""SELECT DISTINCT a.id,e.student_id FROM core.attendance a JOIN core.subjects s ON s.id=a.subject_id JOIN core.enrollments e ON e.class_id=s.class_id JOIN core.users u ON u.id=e.student_id WHERE a.id IN ($placeholders) AND e.status IN ('active','ended') AND u.role='student' AND u.active AND u.email_confirmed_at IS NOT NULL AND $scope""", { rs, _ -> rs.getString(1) to rs.getString(2) }, *ids.toTypedArray()).groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    }

    private fun assignedSubjects(actorId: String, subjectIds: List<String>): Set<String> {
        val placeholders = subjectIds.joinToString(",") { "?" }
        return jdbc.query("SELECT st.subject_id FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.teacher_id=? AND st.subject_id IN ($placeholders) AND u.active AND u.role='teacher'", { rs, _ -> rs.getString(1) }, *(listOf(actorId) + subjectIds).toTypedArray()).toSet()
    }

    private fun requireWriter(actorId: String, hash: String, classId: String, subjectId: String, activeClass: Boolean, lock: Boolean = false) {
        if (lock) lockAuthInvariant()
        val currentRole = requireSession(actorId, hash, "admin", "teacher")
        val active = jdbc.query("SELECT active FROM core.classes WHERE id=?${if (lock) " FOR UPDATE" else ""}", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: throw AcademicNotFound()
        if (activeClass && !active) throw AcademicForbidden()
        if (lock) jdbc.query("SELECT id FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, actorId)
        jdbc.query("SELECT id FROM core.subjects WHERE id=? AND class_id=?${if (lock) " FOR UPDATE" else ""}", { rs, _ -> rs.getString(1) }, subjectId, classId).firstOrNull() ?: throw AcademicNotFound()
        requireSession(actorId, hash, "admin", "teacher")
        if (currentRole == "teacher" && !assigned(actorId, subjectId)) throw AcademicForbidden()
        if (currentRole != "admin" && currentRole != "teacher") throw AcademicForbidden()
    }

    private fun requireSession(actorId: String, hash: String, vararg roles: String): String {
        val roleList = roles.joinToString(",") { "?" }
        val args = listOf(actorId) + roles + listOf(hash, Timestamp.from(clock.instant()))
        return jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND u.role IN ($roleList) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, *args.toTypedArray()).firstOrNull() ?: throw AcademicForbidden()
    }

    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun assigned(actorId: String, subjectId: String) = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=? AND st.teacher_id=? AND u.active AND u.role='teacher')", Boolean::class.java, subjectId, actorId) == true
    private fun subjectClass(subjectId: String) = jdbc.query("SELECT class_id FROM core.subjects WHERE id=?", { rs, _ -> rs.getString(1) }, subjectId).firstOrNull()
    private fun datedEnrollment(date: String) = "(e.enrolled_at IS NOT NULL AND (e.enrolled_at AT TIME ZONE 'America/Sao_Paulo')::date<=$date AND (e.ended_at IS NULL OR (e.ended_at AT TIME ZONE 'America/Sao_Paulo')::date>=$date))"
    private fun legacyEnrollment(date: String, proof: String) = "(e.legacy AND e.enrolled_at IS NULL AND $proof AND (e.ended_at IS NULL OR (e.ended_at AT TIME ZONE 'America/Sao_Paulo')::date>=$date))"
    private fun studentEnrolled(studentId: String, classId: String, date: LocalDate, provenLegacy: Boolean) = jdbc.queryForObject("""SELECT EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.student_id=? AND e.class_id=? AND e.status IN ('active','ended') AND u.active AND u.email_confirmed_at IS NOT NULL AND (${datedEnrollment("?")} OR (? AND ${legacyEnrollment("?", "true")})) )""", Boolean::class.java, studentId, classId, date, date, provenLegacy, date) == true
    private fun markableIds(classId: String, date: LocalDate) = jdbc.query("""SELECT DISTINCT u.id FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=? AND e.status IN ('active','ended') AND u.role='student' AND u.active AND u.email_confirmed_at IS NOT NULL AND ${datedEnrollment("?")}""", { rs, _ -> rs.getString(1) }, classId, date, date).toSet()
    private fun preservedLegacyIds(classId: String, date: LocalDate, records: Map<String, Boolean>): Set<String> {
        if (records.isEmpty()) return emptySet()
        val ids = records.keys.toList()
        val placeholders = ids.joinToString(",") { "?" }
        val args: List<Any> = listOf(classId) + ids + listOf(date)
        return jdbc.query("""SELECT DISTINCT u.id FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=? AND e.student_id IN ($placeholders) AND e.status IN ('active','ended') AND u.role='student' AND u.active AND u.email_confirmed_at IS NOT NULL AND e.legacy AND e.enrolled_at IS NULL AND (e.ended_at IS NULL OR (e.ended_at AT TIME ZONE 'America/Sao_Paulo')::date>=?)""", { rs, _ -> rs.getString(1) }, *args.toTypedArray()).toSet()
    }
    private fun staffRow(id: String) = jdbc.query("SELECT a.*,s.class_id FROM core.attendance a JOIN core.subjects s ON s.id=a.subject_id WHERE a.id=?", { rs, _ -> row(rs) }, id).firstOrNull()
    private fun row(rs: ResultSet): Map<String, Any?> {
        return mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(), "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "subjectId" to rs.getString("subject_id"), "classId" to rs.getString("class_id"), "date" to rs.getObject("class_date", LocalDate::class.java), "time" to rs.getObject("class_time", LocalTime::class.java), "records" to records(rs.getString("records")))
    }
    @Suppress("UNCHECKED_CAST")
    private fun records(value: String) = mapper.readValue(value, Map::class.java) as Map<String, Boolean>
    private fun audit(actorId: String, eventType: String, id: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), eventType, id, actorId) }
}
