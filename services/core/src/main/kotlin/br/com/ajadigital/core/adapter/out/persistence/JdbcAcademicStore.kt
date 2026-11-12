package br.com.ajadigital.core.adapter.out.persistence

import br.com.ajadigital.core.ports.out.AcademicPage
import br.com.ajadigital.core.ports.out.AcademicStore
import br.com.ajadigital.core.ports.out.ProvisionedIdentity
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcAcademicStore(private val jdbc: JdbcTemplate, private val clock: Clock) : AcademicStore {
    override fun isCurrentAdminSession(actorId: String, sessionTokenHash: String, serializeAdminInvariant: Boolean): Boolean {
        if (serializeAdminInvariant) jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java)
        val account = jdbc.query("SELECT role,active FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) == "admin" && rs.getBoolean(2) }, actorId).firstOrNull() ?: return false
        if (!account) return false
        return jdbc.query("SELECT id FROM core.sessions WHERE token_hash=? AND user_id=? AND revoked_at IS NULL AND expires_at>? FOR UPDATE", { rs, _ -> rs.getString(1) }, sessionTokenHash, actorId, Timestamp.from(clock.instant())).isNotEmpty()
    }
    override fun listUsers(page: Int, size: Int, sort: String, role: String?, active: Boolean?, query: String?): AcademicPage {
        val sqlWhere = when {
            role == null && active == null && query.isNullOrBlank() -> ""
            else -> " WHERE " + buildList {
                if (role != null) add("role=?")
                if (active != null) add("active=?")
                if (!query.isNullOrBlank()) add("(name ILIKE ? OR email ILIKE ?)")
            }.joinToString(" AND ")
        }
        val parameters = mutableListOf<Any>().apply {
            role?.let(::add); active?.let(::add); query?.takeIf(String::isNotBlank)?.let { add("%$it%"); add("%$it%") }
        }
        return page("SELECT * FROM core.users$sqlWhere ORDER BY id${sortDirection(sort)}", "SELECT count(*) FROM core.users$sqlWhere", parameters, page, size, ::userMap)
    }

    override fun findUser(id: String) = jdbc.query("SELECT * FROM core.users WHERE id=?", { rs, _ -> userMap(rs) }, id).firstOrNull()

    @Transactional
    override fun createUser(identity: ProvisionedIdentity): Map<String, Any?> {
        val email = identity.email.trim()
        jdbc.update("""INSERT INTO core.users(id,revision,created_at,updated_at,updated_by,name,email,normalized_email,password_hash,role,active,email_confirmed_at)
            VALUES(?,1,?,?,?,?,?,?,?,?,true,NULL)""", identity.id, Timestamp.from(identity.now), Timestamp.from(identity.now), identity.actorId, identity.name, email, email.lowercase(), identity.passwordHash, identity.role)
        jdbc.update("INSERT INTO core.auth_tokens(id,user_id,purpose,token_hash,email_at_issue,created_at,expires_at) VALUES(?,?,'password_reset',?,?,?,?)",
            identity.passwordTokenId, identity.id, identity.passwordTokenHash, email, Timestamp.from(identity.now), Timestamp.from(identity.now.plusSeconds(1800)))
        jdbc.update("INSERT INTO core.auth_tokens(id,user_id,purpose,token_hash,email_at_issue,created_at,expires_at) VALUES(?,?,'email_confirmation',?,?,?,?)",
            identity.confirmationTokenId, identity.id, identity.confirmationTokenHash, email, Timestamp.from(identity.now), Timestamp.from(identity.now.plusSeconds(1800)))
        outbox(identity.passwordEventId, "identity.password-reset-requested", identity.now, identity.keyId, identity.passwordNonce, identity.passwordCiphertext)
        outbox(identity.confirmationEventId, "identity.email-confirmation-requested", identity.now, identity.keyId, identity.confirmationNonce, identity.confirmationCiphertext)
        audit(identity.actorId, "academic.user_created", identity.id)
        return findUser(identity.id)!!
    }

    @Transactional
    override fun updateUser(id: String, actorId: String, expectedRevision: Int, name: String?, email: String?): Map<String, Any?>? {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java)
        val current = findUser(id) ?: return null
        val newName = name ?: current["name"] as String
        val newEmail = email ?: current["email"] as String
        require(newName.isNotBlank() && newName.length <= 200 && br.com.ajadigital.core.domain.identity.EmailAddress.isValid(newEmail))
        val emailChanged = newEmail.lowercase() != (current["email"] as String).lowercase()
        val now = Timestamp.from(clock.instant())
        val changed = jdbc.update("""UPDATE core.users SET name=?,email=?,normalized_email=?,email_confirmed_at=CASE WHEN ? THEN NULL ELSE email_confirmed_at END,
            revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?""",
            newName, newEmail, newEmail.lowercase(), emailChanged, now, actorId, id, expectedRevision)
        if (changed != 1) return null
        if (emailChanged) {
            jdbc.update("UPDATE core.sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL", id)
            jdbc.update("UPDATE core.auth_tokens SET used_at=now() WHERE user_id=? AND used_at IS NULL", id)
        }
        audit(actorId, "academic.user_updated", id)
        return findUser(id)
    }

    @Transactional
    override fun changeRole(id: String, actorId: String, expectedRevision: Int, role: String): Map<String, Any?>? {
        require(role in setOf("student", "teacher", "admin"))
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java)
        val target = jdbc.query("SELECT role,active,revision FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> Triple(rs.getString(1), rs.getBoolean(2), rs.getInt(3)) }, id).firstOrNull() ?: return null
        if (target.third != expectedRevision) return null
        if (target.first == "admin" && target.second && role != "admin") protectLastAdmin(id)
        if (jdbc.update("UPDATE core.users SET role=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", role, Timestamp.from(clock.instant()), actorId, id, expectedRevision) != 1) return null
        jdbc.update("UPDATE core.sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL", id)
        audit(actorId, "academic.user_role_changed", id)
        return findUser(id)
    }

    @Transactional
    override fun deactivateUser(id: String, actorId: String, expectedRevision: Int) = deactivate(id, actorId, expectedRevision, "academic.user_deactivated")

    @Transactional
    override fun withdrawUser(id: String, actorId: String, expectedRevision: Int) = deactivate(id, actorId, expectedRevision, "academic.user_withdrawn")

    private fun deactivate(id: String, actorId: String, expectedRevision: Int, eventType: String): Map<String, Any?>? {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java)
        val target = jdbc.query("SELECT role,active,revision FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> Triple(rs.getString(1), rs.getBoolean(2), rs.getInt(3)) }, id).firstOrNull() ?: return null
        if (target.third != expectedRevision) return null
        if (target.first == "admin" && target.second) protectLastAdmin(id)
        if (jdbc.update("UPDATE core.users SET active=false,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", Timestamp.from(clock.instant()), actorId, id, expectedRevision) != 1) return null
        jdbc.update("UPDATE core.sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL", id)
        audit(actorId, eventType, id)
        return findUser(id)
    }

    private fun protectLastAdmin(id: String) {
        if ((jdbc.queryForObject("SELECT count(*) FROM core.users WHERE role='admin' AND active", Int::class.java) ?: 0) <= 1) throw IllegalStateException("last administrator")
    }

    override fun listClasses(actorId: String, role: String, page: Int, size: Int, sort: String, active: Boolean?, query: String?): AcademicPage {
        val predicates = mutableListOf<String>(); val args = mutableListOf<Any>()
        when (role) {
            "student" -> { predicates += "active AND id IN (SELECT class_id FROM core.enrollments WHERE student_id=? AND status='active')"; args += actorId }
            "teacher" -> predicates += "false" // B-06 introduces staff assignments; no assignment exists in B-04.
            else -> Unit
        }
        active?.let { predicates += "active=?"; args += it }; query?.takeIf(String::isNotBlank)?.let { predicates += "(name ILIKE ? OR code ILIKE ?)"; args += "%$it%"; args += "%$it%" }
        val w = if (predicates.isEmpty()) "" else " WHERE ${predicates.joinToString(" AND ")}"
        return page("SELECT * FROM core.classes$w ORDER BY id${sortDirection(sort)}", "SELECT count(*) FROM core.classes$w", args, page, size) { rs -> classMap(rs, role == "admin" || role == "teacher") }
    }

    override fun findClass(id: String, actorId: String, role: String): Map<String, Any?>? {
        val scope = when (role) {
            "student" -> " AND c.active AND EXISTS(SELECT 1 FROM core.enrollments e WHERE e.class_id=c.id AND e.student_id=? AND e.status='active')"
            "teacher" -> " AND false"
            else -> ""
        }
        val args = if (role == "student") arrayOf(id, actorId) else arrayOf(id)
        return jdbc.query("SELECT c.* FROM core.classes c WHERE c.id=?$scope", { rs, _ -> classMap(rs, role == "admin" || role == "teacher") }, *args).firstOrNull()
    }

    @Transactional
    override fun createClass(actorId: String, name: String, code: String, year: Int, shift: String): Map<String, Any?> {
        require(name.isNotBlank() && code.isNotBlank() && year in 1900..2200 && shift in setOf("Manhã", "Tarde", "Noite"))
        val id = UUID.randomUUID().toString(); val now = Timestamp.from(clock.instant())
        jdbc.update("INSERT INTO core.classes(id,revision,created_at,updated_at,updated_by,name,code,normalized_code,year,shift,active) VALUES(?,1,?,?,?,?,?,?,?,?,true)", id, now, now, actorId, name, code, code.lowercase(), year, shift)
        audit(actorId, "academic.class_created", id)
        return findClass(id, actorId, "admin")!!
    }

    @Transactional
    override fun updateClass(id: String, actorId: String, expectedRevision: Int, name: String?, code: String?, year: Int?, shift: String?): Map<String, Any?>? {
        val current = jdbc.query("SELECT * FROM core.classes WHERE id=?", { rs, _ -> classMap(rs) }, id).firstOrNull() ?: return null
        val n = name ?: current["name"] as String; val c = code ?: current["code"] as String; val y = year ?: current["year"] as Int; val s = shift ?: current["shift"] as String
        require(n.isNotBlank() && c.isNotBlank() && y in 1900..2200 && s in setOf("Manhã", "Tarde", "Noite"))
        if (jdbc.update("UPDATE core.classes SET name=?,code=?,normalized_code=?,year=?,shift=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", n, c, c.lowercase(), y, s, Timestamp.from(clock.instant()), actorId, id, expectedRevision) != 1) return null
        audit(actorId, "academic.class_updated", id)
        return findClass(id, actorId, "admin")
    }

    @Transactional
    override fun setClassActive(id: String, actorId: String, expectedRevision: Int, active: Boolean): Map<String, Any?>? {
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.classes SET active=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", active, now, actorId, id, expectedRevision) != 1) return null
        if (!active) {
            jdbc.update("UPDATE core.enrollments SET status='cancelled',revision=revision+1,updated_at=?,updated_by=? WHERE class_id=? AND status='pending'", now, actorId, id)
            jdbc.update("UPDATE core.enrollments SET status='ended',revision=revision+1,updated_at=?,updated_by=?,ended_at=? WHERE class_id=? AND status='active'", now, actorId, now, id)
        }
        audit(actorId, if (active) "academic.class_reopened" else "academic.class_closed", id)
        return findClass(id, actorId, "admin")
    }

    override fun listEnrollments(actorId: String, role: String, page: Int, size: Int, sort: String, classId: String?, studentId: String?, status: List<String>?, query: String?): AcademicPage {
        val p = mutableListOf<String>(); val a = mutableListOf<Any>()
        when (role) {
            "student" -> { p += "(e.student_id=? OR (e.student_id IS NULL AND e.normalized_email=(SELECT normalized_email FROM core.users WHERE id=?)))"; a += actorId; a += actorId }
            "teacher" -> p += "false" // No staff assignment table exists until B-06.
            else -> Unit
        }
        classId?.let { p += "e.class_id=?"; a += it }; studentId?.let { p += "e.student_id=?"; a += it }
        status?.let { p += "e.status IN (${it.joinToString(",") { "?" }})"; a.addAll(it) }
        query?.takeIf(String::isNotBlank)?.let { p += "(e.email ILIKE ? OR u.name ILIKE ?)"; a += "%$it%"; a += "%$it%" }
        val where = if (p.isEmpty()) "" else " WHERE ${p.joinToString(" AND ")}"
        val from = " FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id LEFT JOIN core.users u ON (u.id=e.student_id OR (e.student_id IS NULL AND u.normalized_email=e.normalized_email))"
        return page("SELECT e.*,c.name class_name$from$where ORDER BY e.id${sortDirection(sort)}", "SELECT count(*)$from$where", a, page, size, ::enrollmentMap)
    }

    override fun findEnrollment(id: String) = jdbc.query("SELECT e.*,c.name class_name FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE e.id=?", { rs, _ -> enrollmentMap(rs) }, id).firstOrNull()

    @Transactional
    override fun createEnrollment(actorId: String, classId: String, email: String): Map<String, Any?> {
        val normalized = email.lowercase()
        val existing = jdbc.query("SELECT e.*,c.name class_name FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE class_id=? AND normalized_email=? AND status IN ('pending','active')", { rs, _ -> enrollmentMap(rs) }, classId, normalized).firstOrNull()
        if (existing != null) return existing
        val student = jdbc.query("SELECT id,active,email_confirmed_at FROM core.users WHERE normalized_email=? FOR UPDATE", { rs, _ -> Triple(rs.getString(1), rs.getBoolean(2), rs.getTimestamp(3)?.toInstant()) }, normalized).firstOrNull()
        val clazz = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: throw NoSuchElementException()
        require(clazz)
        val afterLock = jdbc.query("SELECT e.*,c.name class_name FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE e.class_id=? AND e.normalized_email=? AND e.status IN ('pending','active')", { rs, _ -> enrollmentMap(rs) }, classId, normalized).firstOrNull()
        if (afterLock != null) return afterLock
        if (student?.second == false) throw IllegalStateException("account is inactive")
        val active = student?.second == true && student.third != null
        if (active && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.enrollments WHERE student_id=? AND status='active')", Boolean::class.java, student!!.first) == true) throw IllegalStateException("already enrolled")
        val id = UUID.randomUUID().toString(); val now = Timestamp.from(clock.instant())
        jdbc.update("""INSERT INTO core.enrollments(id,revision,created_at,updated_at,updated_by,class_id,email,normalized_email,student_id,status,created_by,enrolled_at)
            VALUES(?,1,?,?,?,?,?,?,?,?,?,?)""", id, now, now, actorId, classId, email, normalized, student?.first, if (active) "active" else "pending", actorId, if (active) now else null)
        audit(actorId, "academic.enrollment_created", id)
        return findEnrollment(id)!!
    }

    override fun enrollmentPreview(classId: String, email: String): Map<String, Any?>? {
        val clazz = jdbc.query("SELECT id FROM core.classes WHERE id=? AND active", { rs, _ -> rs.getString(1) }, classId).firstOrNull() ?: return null
        val normalized = email.lowercase()
        val accountId = jdbc.query("SELECT id FROM core.users WHERE normalized_email=?", { rs, _ -> rs.getString(1) }, normalized).firstOrNull()
        val account = accountId?.let(::findUser)
        val enrollment = jdbc.query("SELECT e.*,c.name class_name FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE e.class_id=? AND e.normalized_email=? AND e.status IN ('pending','active')", { rs, _ -> enrollmentMap(rs) }, classId, normalized).firstOrNull()
        val other = jdbc.query("SELECT c.id,c.name FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE e.normalized_email=? AND e.status='active' AND e.class_id<>?", { rs, _ -> mapOf("id" to rs.getString(1), "name" to rs.getString(2)) }, normalized, classId).firstOrNull()
        val can = enrollment == null && other == null && account?.get("active") != false
        return mapOf("email" to email, "classId" to clazz, "account" to account, "existingEnrollment" to enrollment, "otherClass" to other, "canEnroll" to can, "message" to if (can) null else "Enrollment is unavailable")
    }

    @Transactional
    override fun transitionEnrollment(id: String, actorId: String, expectedRevision: Int, command: String): Map<String, Any?>? {
        val pointer = jdbc.query("SELECT class_id,student_id,normalized_email FROM core.enrollments WHERE id=?", { rs, _ -> Triple(rs.getString("class_id"), rs.getString("student_id"), rs.getString("normalized_email")) }, id).firstOrNull() ?: return null
        val activationAccount = if (command == "activate") {
            if (pointer.second == null) {
                jdbc.query("SELECT id,active,email_confirmed_at FROM core.users WHERE normalized_email=? FOR UPDATE", { rs, _ -> Triple(rs.getString(1), rs.getBoolean(2), rs.getTimestamp(3)?.toInstant()) }, pointer.third).firstOrNull()
            } else {
                jdbc.query("SELECT id,active,email_confirmed_at FROM core.users WHERE id=? FOR UPDATE", { rs, _ -> Triple(rs.getString(1), rs.getBoolean(2), rs.getTimestamp(3)?.toInstant()) }, pointer.second).firstOrNull()
            }
        } else null
        val classId = pointer.first
        val classActive = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: return null
        val current = jdbc.query("SELECT status,revision,student_id FROM core.enrollments WHERE id=? FOR UPDATE",
            { rs, _ -> EnrollmentTransition(rs.getString("status"), rs.getInt("revision"), rs.getString("student_id"), classActive) }, id).firstOrNull() ?: return null
        if (current.revision != expectedRevision) return null
        var studentId = current.studentId
        val status = when (command) {
            "cancel" -> if (current.status in setOf("pending", "active")) "cancelled" else return null
            "end" -> if (current.status == "active") "ended" else return null
            "activate" -> {
                if (current.status != "pending" || !current.classActive) return null
                val account = activationAccount
                if (account == null || !account.second || account.third == null) throw IllegalStateException("identity is not confirmed")
                studentId = account.first
                if (jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.enrollments WHERE student_id=? AND status='active' AND id<>?)", Boolean::class.java, studentId, id) == true) throw IllegalStateException("student already enrolled")
                "active"
            }
            else -> throw IllegalArgumentException()
        }
        val now = Timestamp.from(clock.instant())
        val enrolledAt = if (status == "active") now else null
        jdbc.update("UPDATE core.enrollments SET student_id=?,status=?,revision=revision+1,updated_at=?,updated_by=?,enrolled_at=COALESCE(enrolled_at,?),ended_at=? WHERE id=? AND revision=?", studentId, status, now, actorId, enrolledAt, if (status == "ended") now else null, id, expectedRevision)
        audit(actorId, when (status) { "active" -> "academic.enrollment_activated"; "cancelled" -> "academic.enrollment_cancelled"; else -> "academic.enrollment_ended" }, id)
        return findEnrollment(id)
    }

    private data class EnrollmentTransition(val status: String, val revision: Int, val studentId: String?, val classActive: Boolean)

    override fun myEnrollments(studentId: String, page: Int, size: Int, sort: String) = listEnrollmentsForStudent(studentId, page, size, false, sort)

    override fun listStudents(actorId: String, role: String, page: Int, size: Int, sort: String, classId: String?, query: String?, history: Boolean): AcademicPage {
        val predicates = mutableListOf<String>("u.role='student'", "(e.status='active'${if (history) " OR e.status='ended'" else ""})")
        val args = mutableListOf<Any>()
        if (role == "teacher" || role == "student") predicates += "false" // B-06 introduces staff assignments; student records are staff-only.
        classId?.let { predicates += "e.class_id=?"; args += it }
        query?.takeIf(String::isNotBlank)?.let { predicates += "(u.name ILIKE ? OR u.email ILIKE ?)"; args += "%$it%"; args += "%$it%" }
        val where = predicates.joinToString(" AND ")
        val sql = "SELECT DISTINCT u.* FROM core.users u JOIN core.enrollments e ON e.student_id=u.id WHERE $where ORDER BY u.id${sortDirection(sort)}"
        val count = "SELECT count(DISTINCT u.id) FROM core.users u JOIN core.enrollments e ON e.student_id=u.id WHERE $where"
        return page(sql, count, args, page, size) { rs -> summaryMap(rs, classId) }
    }

    override fun findStudent(id: String, actorId: String, role: String): Map<String, Any?>? {
        if (role != "admin") return null // Teacher detail is hidden until an assigned class context exists.
        val student = jdbc.query("SELECT * FROM core.users WHERE id=? AND role='student'", { rs, _ -> userMap(rs) }, id).firstOrNull() ?: return null
        val enrollments = jdbc.query("SELECT e.*,c.name class_name FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE e.student_id=? ORDER BY e.created_at", { rs, _ -> enrollmentMap(rs) }, id)
        return mapOf("student" to summaryMap(student, null), "enrollments" to enrollments, "attendance" to emptyList<Any>(), "attempts" to emptyList<Any>(), "submissions" to emptyList<Any>())
    }

    private fun listEnrollmentsForStudent(studentId: String, page: Int, size: Int, includeEnded: Boolean, sort: String = "id"): AcademicPage {
        val status = if (includeEnded) "('active','ended')" else "('active','pending','ended','cancelled')"
        val from = " FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE (e.student_id=? OR (e.student_id IS NULL AND e.normalized_email=(SELECT normalized_email FROM core.users WHERE id=?))) AND e.status IN $status"
        return page("SELECT e.*,c.name class_name$from ORDER BY e.id${sortDirection(sort)}", "SELECT count(*)$from", listOf(studentId, studentId), page, size, ::enrollmentMap)
    }

    private fun outbox(id: String, type: String, now: java.time.Instant, keyId: String, nonce: ByteArray, cipher: ByteArray) {
        jdbc.update("INSERT INTO core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload) VALUES(?,?,1,?,?,?,?)", id, type, Timestamp.from(now), keyId, nonce, cipher)
    }
    private fun audit(actorId: String, eventType: String, subjectId: String) {
        jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), eventType, subjectId, actorId)
    }
    private fun sortDirection(sort: String) = if (sort == "-id") " DESC" else " ASC"

    private fun <T> page(sql: String, countSql: String, args: List<Any>, page: Int, size: Int, mapper: (ResultSet) -> T): AcademicPage {
        val total = jdbc.queryForObject(countSql, Int::class.java, *args.toTypedArray()) ?: 0
        val items = jdbc.query("$sql LIMIT ? OFFSET ?", { rs, _ -> mapper(rs) }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return AcademicPage(items as List<Map<String, Any?>>, page, size, total)
    }

    private fun userMap(rs: ResultSet) = mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant(), "updatedAt" to rs.getTimestamp("updated_at").toInstant(), "updatedBy" to rs.getString("updated_by"), "name" to rs.getString("name"), "email" to rs.getString("email"), "role" to rs.getString("role"), "active" to rs.getBoolean("active"), "emailConfirmedAt" to rs.getTimestamp("email_confirmed_at")?.toInstant())
    private fun summaryMap(rs: ResultSet, classId: String?): Map<String, Any?> = summaryMap(userMap(rs), classId)
    private fun summaryMap(user: Map<String, Any?>, classId: String?): Map<String, Any?> {
        val id = user["id"] as String
        val enrollment = classId?.let { contextClassId -> jdbc.query("SELECT e.*,c.name class_name FROM core.enrollments e JOIN core.classes c ON c.id=e.class_id WHERE e.student_id=? AND e.status IN ('active','ended') AND e.class_id=? ORDER BY e.updated_at DESC LIMIT 1", { r, _ -> enrollmentMap(r) }, id, contextClassId).firstOrNull() }
        return mapOf("id" to id, "name" to user["name"], "email" to user["email"], "active" to user["active"], "enrollment" to enrollment)
    }
    private fun classMap(rs: ResultSet, includeStudents: Boolean = true): Map<String, Any?> {
        val id = rs.getString("id")
        val result = mutableMapOf<String, Any?>("id" to id, "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant(), "updatedAt" to rs.getTimestamp("updated_at").toInstant(), "updatedBy" to rs.getString("updated_by"), "name" to rs.getString("name"), "code" to rs.getString("code"), "year" to rs.getInt("year"), "shift" to rs.getString("shift"), "active" to rs.getBoolean("active"))
        if (includeStudents) result["studentIds"] = jdbc.query("SELECT student_id FROM core.enrollments WHERE class_id=? AND status='active' AND student_id IS NOT NULL ORDER BY student_id", { r, _ -> r.getString(1) }, id)
        return result
    }
    private fun enrollmentMap(rs: ResultSet): Map<String, Any?> = mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant(), "updatedAt" to rs.getTimestamp("updated_at").toInstant(), "updatedBy" to rs.getString("updated_by"), "classId" to rs.getString("class_id"), "email" to rs.getString("email"), "studentId" to rs.getString("student_id"), "status" to rs.getString("status"), "createdBy" to rs.getString("created_by"), "enrolledAt" to rs.getTimestamp("enrolled_at")?.toInstant(), "endedAt" to rs.getTimestamp("ended_at")?.toInstant(), "legacy" to rs.getBoolean("legacy"), "unassignedAssessmentIds" to emptyList<String>(), "className" to rs.getString("class_name"))
}
