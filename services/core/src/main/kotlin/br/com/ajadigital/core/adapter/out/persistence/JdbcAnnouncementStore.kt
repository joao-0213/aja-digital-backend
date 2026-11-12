package br.com.ajadigital.core.adapter.out.persistence

import br.com.ajadigital.core.application.academic.AcademicConflict
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.application.identity.OutboxEncryption
import br.com.ajadigital.core.ports.out.AcademicPage
import br.com.ajadigital.core.ports.out.AnnouncementDraft
import br.com.ajadigital.core.ports.out.AnnouncementOccurrence
import br.com.ajadigital.core.ports.out.AnnouncementStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.text.Normalizer
import java.util.UUID

@Repository
class JdbcAnnouncementStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val encryption: OutboxEncryption,
) : AnnouncementStore {
    @Transactional
    override fun authorizeCreate(actorId: String, role: String, sessionHash: String, draft: AnnouncementDraft) {
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        lockAudience(draft.classId, draft.subjectId, actorId, role)
    }

    @Transactional
    override fun create(actorId: String, role: String, sessionHash: String, draft: AnnouncementDraft, key: String, requestHash: String): Map<String, Any?> {
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        lockAudience(draft.classId, draft.subjectId, actorId, role)
        val id = UUID.randomUUID().toString()
        val now = Timestamp.from(clock.instant())
        jdbc.update("""INSERT INTO core.announcements(id,revision,created_at,updated_at,updated_by,created_by,class_id,subject_id,title,body,notify,occurrence)
            VALUES(?,1,?,?,?,?,?,?,?,?,?,?::jsonb)""", id, now, now, actorId, actorId, draft.classId, draft.subjectId, draft.title.trim(), draft.body, draft.notify, draft.occurrence?.let(mapper::writeValueAsString))
        if (draft.notify) notify(id, 1, actorId, draft)
        audit(actorId, "academic.announcement_created", id)
        return announcement(actorId, role, sessionHash, id) ?: throw AcademicNotFound()
    }

    @Transactional
    override fun update(actorId: String, role: String, sessionHash: String, id: String, revision: Int, draft: AnnouncementDraft): Map<String, Any?>? {
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        val current = jdbc.query("SELECT * FROM core.announcements WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> row(rs) }, id).firstOrNull() ?: return null
        if (current["revision"] != revision) return null
        lockAudience(current["classId"] as String, current["subjectId"] as String?, actorId, role)
        lockAudience(draft.classId, draft.subjectId, actorId, role)
        if (draft.copy(title = draft.title.trim()) == currentDraft(current)) return announcement(actorId, role, sessionHash, id)
        val now = Timestamp.from(clock.instant())
        val nextRevision = revision + 1
        if (jdbc.update("""UPDATE core.announcements SET revision=?,updated_at=?,updated_by=?,class_id=?,subject_id=?,title=?,body=?,notify=?,occurrence=?::jsonb
            WHERE id=? AND revision=? AND withdrawn_at IS NULL""", nextRevision, now, actorId, draft.classId, draft.subjectId, draft.title.trim(), draft.body, draft.notify, draft.occurrence?.let(mapper::writeValueAsString), id, revision) != 1) return null
        if (draft.notify) notify(id, nextRevision, actorId, draft)
        audit(actorId, "academic.announcement_updated", id)
        return announcement(actorId, role, sessionHash, id)
    }

    @Transactional
    override fun withdraw(actorId: String, role: String, sessionHash: String, id: String, revision: Int): Map<String, Any?>? {
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        val current = jdbc.query("SELECT * FROM core.announcements WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> row(rs) }, id).firstOrNull() ?: return null
        if (current["revision"] != revision) return null
        lockAudience(current["classId"] as String, current["subjectId"] as String?, actorId, role)
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.announcements SET revision=revision+1,updated_at=?,updated_by=?,withdrawn_at=? WHERE id=? AND revision=? AND withdrawn_at IS NULL", now, actorId, now, id, revision) != 1) return null
        audit(actorId, "academic.announcement_withdrawn", id)
        return dto(current + mapOf("revision" to revision + 1, "updatedAt" to now.toInstant().toString(), "updatedBy" to actorId, "authorName" to jdbc.queryForObject("SELECT name FROM core.users WHERE id=?", String::class.java, current["createdBy"] as String)))
    }

    override fun announcement(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        requireReaderSession(actorId, role, sessionHash)
        val row = jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE a.id=? AND a.withdrawn_at IS NULL AND ${access("a", role)}""", { rs, _ -> row(rs) + mapOf("authorName" to rs.getString("author_name")) }, *(listOf(id) + accessArgs(actorId, role)).toTypedArray()).firstOrNull() ?: return null
        return dto(row)
    }

    override fun announcements(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?): AcademicPage {
        requireReaderSession(actorId, role, sessionHash)
        val clauses = mutableListOf("a.withdrawn_at IS NULL", access("a", role))
        val args = accessArgs(actorId, role).toMutableList<Any>()
        if (classId != null) { clauses += "a.class_id=?"; args += classId }
        if (subjectId != null) { clauses += "a.subject_id=?"; args += subjectId }
        val where = clauses.joinToString(" AND ")
        val direction = if (sort == "-createdAt") "DESC" else "ASC"
        val matches = jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE $where ORDER BY a.created_at $direction,a.id""", { rs, _ -> dto(row(rs) + mapOf("authorName" to rs.getString("author_name"))) }, *args.toTypedArray())
            .filter { query.isNullOrBlank() || normalize("${it["title"]} ${it["body"]}").contains(normalize(query)) }
        val items = matches.drop((page - 1) * size).take(size)
        return AcademicPage(items, page, size, matches.size)
    }

    override fun calendar(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, from: String?, to: String?, subjectId: String?): AcademicPage {
        requireReaderSession(actorId, role, sessionHash)
        val items = mutableListOf<Map<String, Any?>>()
        val announcementArgs = accessArgs(actorId, role).toMutableList<Any>()
        val announcementFilters = mutableListOf("a.withdrawn_at IS NULL", "a.occurrence IS NOT NULL", access("a", role))
        if (subjectId != null) { announcementFilters += "a.subject_id=?"; announcementArgs += subjectId }
        val occurrenceRows = jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE ${announcementFilters.joinToString(" AND ")}""", { rs, _ -> row(rs) + mapOf("authorName" to rs.getString("author_name")) }, *announcementArgs.toTypedArray())
        occurrenceRows.map(::dto).forEach { announcement ->
            val occurrence = announcement["occurrence"] as? Map<*, *> ?: return@forEach
            val date = occurrence["date"] as String
            if ((from == null || date >= from) && (to == null || date <= to)) items += calendarItem(
                announcement["id"] as String, announcement["title"] as String, occurrence, announcement["body"] as String,
                announcement["subjectId"] as String?, "announcement", "/avisos?id=${announcement["id"]}",
            )
        }
        val assessmentArgs = mutableListOf<Any>()
        val assessmentAccess = when (role) {
            "admin" -> "true"
            "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users tu ON tu.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND tu.active AND tu.role='teacher')".also { assessmentArgs += actorId }
            else -> "EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users su ON su.id=e.student_id WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND su.active AND su.email_confirmed_at IS NOT NULL AND c.active AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id))".also { assessmentArgs += actorId }
        }
        val assessmentFilters = mutableListOf("a.status='published'", "a.deadline IS NOT NULL", "m.withdrawn_at IS NULL", assessmentAccess)
        if (subjectId != null) { assessmentFilters += "s.id=?"; assessmentArgs += subjectId }
        val deadlines = jdbc.query("""SELECT a.id,a.title,a.description,a.kind,a.deadline,m.subject_id FROM core.assessments a
            JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
            WHERE ${assessmentFilters.joinToString(" AND ")}""", { rs, _ ->
            val local = rs.getTimestamp("deadline").toInstant().atZone(brasilia)
            mapOf("id" to rs.getString("id"), "title" to rs.getString("title"), "description" to rs.getString("description"), "kind" to rs.getString("kind"), "date" to local.toLocalDate().toString(), "time" to local.toLocalTime().withSecond(0).withNano(0).toString().substring(0, 5), "subjectId" to rs.getString("subject_id"))
        }, *assessmentArgs.toTypedArray())
        deadlines.filter { (from == null || it.getValue("date") as String >= from) && (to == null || it.getValue("date") as String <= to) }.forEach { deadline ->
            val occurrence = mapOf("date" to deadline.getValue("date"), "time" to deadline.getValue("time"), "allDay" to false)
            items += calendarItem(deadline.getValue("id") as String, deadline.getValue("title") as String, occurrence, deadline.getValue("description") as String, deadline.getValue("subjectId") as String?, "assessment", "/questionarios?id=${deadline.getValue("id")}", deadline.getValue("kind") as String)
        }
        val direction = if (sort == "-date") -1 else 1
        val tieBreak = compareBy<Map<String, Any?>> { it["allDay"] != true }.thenBy { it["time"] as? String ?: "" }.thenBy { it["id"] as String }
        val ordered = items.sortedWith(Comparator { left, right ->
            val dateOrder = (left["date"] as String).compareTo(right["date"] as String) * direction
            if (dateOrder != 0) dateOrder else tieBreak.compare(left, right)
        })
        return AcademicPage(ordered.drop((page - 1) * size).take(size), page, size, ordered.size)
    }

    override fun dashboardAnnouncements(actorId: String, role: String, sessionHash: String, size: Int): List<Map<String, Any?>> {
        require(size in 1..10)
        requireReaderSession(actorId, role, sessionHash)
        val args = (accessArgs(actorId, role) + listOf<Any>(size)).toTypedArray()
        return jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE a.withdrawn_at IS NULL AND ${access("a", role)}
            ORDER BY a.created_at DESC,a.id LIMIT ?""", { rs, _ -> dto(row(rs) + mapOf("authorName" to rs.getString("author_name"))) }, *args)
    }

    override fun dashboardUpcoming(actorId: String, role: String, sessionHash: String, from: String, to: String, size: Int): List<Map<String, Any?>> {
        require(size in 1..10)
        requireReaderSession(actorId, role, sessionHash)
        val announcementArgs = (accessArgs(actorId, role) + listOf<Any>(from, to, size)).toTypedArray()
        val announcements = jdbc.query("""SELECT a.* FROM core.announcements a
            WHERE a.withdrawn_at IS NULL AND a.occurrence IS NOT NULL AND ${access("a", role)}
              AND a.occurrence->>'date' BETWEEN ? AND ?
            ORDER BY a.occurrence->>'date', (a.occurrence->>'allDay')::boolean DESC, a.occurrence->>'time', a.id LIMIT ?""", { rs, _ ->
            val announcement = row(rs)
            val occurrence = announcement["occurrence"] as? Map<*, *> ?: emptyMap<String, Any?>()
            calendarItem(announcement["id"] as String, announcement["title"] as String, occurrence, announcement["body"] as String,
                announcement["subjectId"] as String?, "announcement", "/avisos?id=${announcement["id"]}")
        }, *announcementArgs)

        val assessmentArgs = mutableListOf<Any>()
        val assessmentAccess = when (role) {
            "admin" -> "true"
            "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users tu ON tu.id=st.teacher_id WHERE st.subject_id=s.id AND st.teacher_id=? AND tu.active AND tu.role='teacher')".also { assessmentArgs += actorId }
            else -> "EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users su ON su.id=e.student_id WHERE e.class_id=s.class_id AND e.student_id=? AND e.status='active' AND su.active AND su.email_confirmed_at IS NOT NULL AND c.active AND NOT jsonb_exists(e.unassigned_assessment_ids,a.id))".also { assessmentArgs += actorId }
        }
        assessmentArgs += from
        assessmentArgs += to
        assessmentArgs += size
        val deadlines = jdbc.query("""SELECT a.id,a.title,a.description,a.kind,a.deadline,m.subject_id,
                (a.deadline AT TIME ZONE 'America/Sao_Paulo')::date local_date,
                to_char(a.deadline AT TIME ZONE 'America/Sao_Paulo','HH24:MI') local_time
            FROM core.assessments a JOIN core.modules m ON m.id=a.module_id JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id
            WHERE a.status='published' AND a.deadline IS NOT NULL AND m.withdrawn_at IS NULL AND $assessmentAccess
              AND (a.deadline AT TIME ZONE 'America/Sao_Paulo')::date BETWEEN ?::date AND ?::date
            ORDER BY local_date,local_time,a.id LIMIT ?""", { rs, _ ->
            val id = rs.getString("id")
            val occurrence = mapOf("date" to rs.getDate("local_date").toLocalDate().toString(), "time" to rs.getString("local_time"), "allDay" to false)
            calendarItem(id, rs.getString("title"), occurrence, rs.getString("description"), rs.getString("subject_id"), "assessment", "/questionarios?id=$id", rs.getString("kind"))
        }, *assessmentArgs.toTypedArray())

        val order = compareBy<Map<String, Any?>> { it["date"] as String }
            .thenBy { it["allDay"] != true }
            .thenBy { it["time"] as? String ?: "" }
            .thenBy { it["id"] as String }
        return (announcements + deadlines).sortedWith(order).take(size)
    }

    private fun notify(id: String, revision: Int, actorId: String, draft: AnnouncementDraft) {
        val recipients = if (draft.subjectId == null) jdbc.query("""SELECT DISTINCT u.id,u.email FROM core.enrollments e JOIN core.users u ON u.id=e.student_id
            JOIN core.classes c ON c.id=e.class_id WHERE e.class_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL AND c.active ORDER BY u.id""", { rs, _ -> rs.getString(1) to rs.getString(2) }, draft.classId)
        else jdbc.query("""SELECT DISTINCT u.id,u.email FROM core.enrollments e JOIN core.users u ON u.id=e.student_id JOIN core.classes c ON c.id=e.class_id
            WHERE e.class_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL AND c.active
            AND EXISTS(SELECT 1 FROM core.subjects s WHERE s.id=? AND s.class_id=e.class_id) ORDER BY u.id""", { rs, _ -> rs.getString(1) to rs.getString(2) }, draft.classId, draft.subjectId)
        recipients.forEach { (recipientId, email) ->
            val eventId = UUID.randomUUID().toString()
            val eventType = "academic.announcement-notification-requested"
            val payload = mapper.writeValueAsBytes(mapOf("recipient" to email, "announcementId" to id, "recipientId" to recipientId, "revision" to revision, "title" to draft.title.trim(), "body" to draft.body))
            val encrypted = encryption.encrypt(eventId, eventType, 1, payload)
            val inserted = jdbc.update("INSERT INTO core.announcement_notification_recipients(announcement_id,revision,recipient_id,event_id) VALUES(?,?,?,?) ON CONFLICT DO NOTHING", id, revision, recipientId, eventId)
            if (inserted == 1) jdbc.update("INSERT INTO core.outbox_events(id,event_type,event_version,created_at,key_id,nonce,encrypted_payload) VALUES(?,?,1,?,?,?,?)", eventId, eventType, Timestamp.from(clock.instant()), encrypted.keyId, encrypted.nonce, encrypted.ciphertext)
        }
    }

    private fun lockAudience(classId: String, subjectId: String?, actorId: String, role: String) {
        val active = jdbc.query("SELECT active FROM core.classes WHERE id=? FOR UPDATE", { rs, _ -> rs.getBoolean(1) }, classId).firstOrNull() ?: throw AcademicNotFound()
        if (!active) throw AcademicForbidden()
        if (subjectId != null) {
            val subjectClass = jdbc.query("SELECT class_id FROM core.subjects WHERE id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, subjectId).firstOrNull() ?: throw AcademicNotFound()
            if (subjectClass != classId) throw AcademicNotFound()
        }
        if (role == "teacher") {
            val assigned = if (subjectId != null) jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=? AND st.teacher_id=? AND u.active AND u.role='teacher')", Boolean::class.java, subjectId, actorId)
            else jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.subjects s ON s.id=st.subject_id JOIN core.users u ON u.id=st.teacher_id WHERE s.class_id=? AND st.teacher_id=? AND u.active AND u.role='teacher')", Boolean::class.java, classId, actorId)
            if (assigned != true) throw AcademicForbidden()
        }
    }

    private fun requireWriterSession(actorId: String, role: String, hash: String) = requireSession(actorId, role, hash, "teacher", "admin")
    private fun requireReaderSession(actorId: String, role: String, hash: String) = requireSession(actorId, role, hash, "student", "teacher", "admin")
    private fun requireSession(actorId: String, role: String, hash: String, vararg allowed: String) {
        if (role !in allowed) throw AcademicForbidden()
        val exists = jdbc.queryForObject("""SELECT EXISTS(SELECT 1 FROM core.users u JOIN core.sessions s ON s.user_id=u.id
            WHERE u.id=? AND u.active AND u.role=? AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?)""", Boolean::class.java, actorId, role, hash, Timestamp.from(clock.instant()))
        if (exists != true) throw AcademicForbidden()
    }

    private fun access(alias: String, role: String) = when (role) {
        "admin" -> "true"
        "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users tu ON tu.id=st.teacher_id JOIN core.subjects s ON s.id=st.subject_id WHERE s.class_id=$alias.class_id AND ($alias.subject_id IS NULL OR s.id=$alias.subject_id) AND st.teacher_id=? AND tu.active AND tu.role='teacher')"
        else -> "EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users su ON su.id=e.student_id JOIN core.classes c ON c.id=e.class_id WHERE e.class_id=$alias.class_id AND e.student_id=? AND e.status='active' AND su.active AND su.role='student' AND su.email_confirmed_at IS NOT NULL AND c.active)"
    }

    private fun accessArgs(actorId: String, role: String) = if (role == "admin") emptyList() else listOf(actorId)
    private fun dto(row: Map<String, Any?>) = mapOf(
        "id" to row.getValue("id"), "revision" to row.getValue("revision"), "createdAt" to row.getValue("createdAt"), "updatedAt" to row.getValue("updatedAt"),
        "updatedBy" to row.getValue("updatedBy"), "classId" to row.getValue("classId"), "subjectId" to row.getValue("subjectId"), "title" to row.getValue("title"),
        "body" to row.getValue("body"), "notify" to row.getValue("notify"), "occurrence" to row.getValue("occurrence"),
        "author" to mapOf("id" to row.getValue("createdBy"), "name" to row.getValue("authorName")),
    )

    private fun currentDraft(row: Map<String, Any?>) = AnnouncementDraft(
        row.getValue("classId") as String,
        row["subjectId"] as String?,
        row.getValue("title") as String,
        row.getValue("body") as String,
        row.getValue("notify") as Boolean,
        (row["occurrence"] as? Map<*, *>)?.let { AnnouncementOccurrence(it["date"] as String, it["time"] as String?, it["allDay"] as Boolean) },
    )

    private fun row(rs: ResultSet): Map<String, Any?> = mapOf(
        "id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(),
        "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "createdBy" to rs.getString("created_by"),
        "classId" to rs.getString("class_id"), "subjectId" to rs.getString("subject_id"), "title" to rs.getString("title"), "body" to rs.getString("body"),
        "notify" to rs.getBoolean("notify"), "occurrence" to rs.getString("occurrence")?.let { mapper.readValue(it, Map::class.java) },
        "withdrawnAt" to rs.getTimestamp("withdrawn_at")?.toInstant(),
    )

    private fun calendarItem(id: String, title: String, occurrence: Map<*, *>, description: String, subjectId: String?, source: String, href: String, kind: String = "event") = mapOf(
        "id" to id, "title" to title, "kind" to if (source == "announcement") "event" else if (kind == "quiz") "exam" else "class",
        "date" to occurrence["date"], "time" to occurrence["time"], "allDay" to occurrence["allDay"], "description" to description,
        "subjectId" to subjectId, "source" to source, "sourceId" to id, "href" to href,
    )

    private fun audit(actorId: String, eventType: String, id: String) = jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), eventType, id, actorId)
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
    private val brasilia = ZoneId.of("America/Sao_Paulo")
}
