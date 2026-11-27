package br.com.ajadigital.core.announcements.adapters.out.persistence

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.outbox.application.ports.`in`.OutboxEventProducerInputPort
import br.com.ajadigital.core.outbox.application.contracts.PrepareOutboxEventCommand
import br.com.ajadigital.core.outbox.application.contracts.AppendPreparedOutboxEventCommand
import br.com.ajadigital.core.shared.application.contracts.Page
import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementAuthor
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementCalendarEvent
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementOccurrenceProjection
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import br.com.ajadigital.core.announcements.domain.models.AnnouncementOccurrence
import br.com.ajadigital.core.announcements.application.models.AuthorizeAnnouncementCreate
import br.com.ajadigital.core.announcements.application.models.CreateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.models.UpdateAnnouncementRecord
import br.com.ajadigital.core.announcements.application.models.WithdrawAnnouncementRecord
import br.com.ajadigital.core.announcements.application.models.GetStoredAnnouncementQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementsStoreQuery
import br.com.ajadigital.core.announcements.application.models.ListAnnouncementCalendarQuery
import br.com.ajadigital.core.announcements.application.models.ListDashboardAnnouncementStoreQuery
import br.com.ajadigital.core.announcements.application.models.ListDashboardUpcomingStoreQuery
import br.com.ajadigital.core.announcements.domain.models.AnnouncementRecipientScope
import br.com.ajadigital.core.announcements.domain.models.AnnouncementRecipient
import br.com.ajadigital.core.announcements.domain.models.AnnouncementNotificationRequested
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementStoreOutputPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.ZoneId
import java.text.Normalizer
import java.util.UUID

@Repository
class JdbcAnnouncementStore(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val outboxEvents: OutboxEventProducerInputPort,
) : AnnouncementStoreOutputPort {
    @Transactional
    override fun authorizeCreate(request: AuthorizeAnnouncementCreate) {
        val (actorId, role, sessionHash, draft) = request
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        lockAudience(draft.classId, draft.subjectId, actorId, role)
    }

    @Transactional
    override fun create(request: CreateAnnouncementRecord): Announcement {
        val (actorId, role, sessionHash, draft, key, requestHash) = request
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        lockAudience(draft.classId, draft.subjectId, actorId, role)
        val id = UUID.randomUUID().toString()
        val now = Timestamp.from(clock.instant())
        jdbc.update("""INSERT INTO core.announcements(id,revision,created_at,updated_at,updated_by,created_by,class_id,subject_id,title,body,notify,occurrence)
            VALUES(?,1,?,?,?,?,?,?,?,?,?,?::jsonb)""", id, now, now, actorId, actorId, draft.classId, draft.subjectId, draft.title.trim(), draft.body, draft.notify, draft.occurrence?.let(mapper::writeValueAsString))
        if (draft.notify) notify(id, 1, draft)
        audit(actorId, "academic.announcement_created", id)
        return announcement(GetStoredAnnouncementQuery(actorId, role, sessionHash, id)) ?: throw AcademicNotFound()
    }

    @Transactional
    override fun update(request: UpdateAnnouncementRecord): Announcement? {
        val (actorId, role, sessionHash, id, revision, draft) = request
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        val current = jdbc.query("SELECT * FROM core.announcements WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> row(rs) }, id).firstOrNull() ?: return null
        if (current.revision != revision) return null
        lockAudience(current.classId, current.subjectId, actorId, role)
        lockAudience(draft.classId, draft.subjectId, actorId, role)
        if (draft.copy(title = draft.title.trim()) == currentDraft(current)) return announcement(GetStoredAnnouncementQuery(actorId, role, sessionHash, id))
        val now = Timestamp.from(clock.instant())
        val nextRevision = revision + 1
        if (jdbc.update("""UPDATE core.announcements SET revision=?,updated_at=?,updated_by=?,class_id=?,subject_id=?,title=?,body=?,notify=?,occurrence=?::jsonb
            WHERE id=? AND revision=? AND withdrawn_at IS NULL""", nextRevision, now, actorId, draft.classId, draft.subjectId, draft.title.trim(), draft.body, draft.notify, draft.occurrence?.let(mapper::writeValueAsString), id, revision) != 1) return null
        if (draft.notify) notify(id, nextRevision, draft)
        audit(actorId, "academic.announcement_updated", id)
        return announcement(GetStoredAnnouncementQuery(actorId, role, sessionHash, id))
    }

    @Transactional
    override fun withdraw(request: WithdrawAnnouncementRecord): Announcement? {
        val (actorId, role, sessionHash, id, revision) = request
        lockAuthInvariant()
        requireWriterSession(actorId, role, sessionHash)
        val current = jdbc.query("SELECT * FROM core.announcements WHERE id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> row(rs) }, id).firstOrNull() ?: return null
        if (current.revision != revision) return null
        lockAudience(current.classId, current.subjectId, actorId, role)
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.announcements SET revision=revision+1,updated_at=?,updated_by=?,withdrawn_at=? WHERE id=? AND revision=? AND withdrawn_at IS NULL", now, actorId, now, id, revision) != 1) return null
        audit(actorId, "academic.announcement_withdrawn", id)
        return current.toAnnouncement(jdbc.queryForObject("SELECT name FROM core.users WHERE id=?", String::class.java, current.createdBy)!!, revision + 1, now.toInstant().toString(), actorId)
    }

    override fun announcement(request: GetStoredAnnouncementQuery): Announcement? {
        val (actorId, role, sessionHash, id) = request
        requireReaderSession(actorId, role, sessionHash)
        val row = jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE a.id=? AND a.withdrawn_at IS NULL AND ${access("a", role)}""", { rs, _ -> row(rs) to rs.getString("author_name") }, *(listOf(id) + accessArgs(actorId, role)).toTypedArray()).firstOrNull() ?: return null
        return row.first.toAnnouncement(row.second)
    }

    override fun announcements(request: ListAnnouncementsStoreQuery): Page<Announcement> {
        val (actorId, role, sessionHash, page, size, sort, classId, subjectId, query) = request
        requireReaderSession(actorId, role, sessionHash)
        val clauses = mutableListOf("a.withdrawn_at IS NULL", access("a", role))
        val args = accessArgs(actorId, role).toMutableList<Any>()
        if (classId != null) { clauses += "a.class_id=?"; args += classId }
        if (subjectId != null) { clauses += "a.subject_id=?"; args += subjectId }
        val where = clauses.joinToString(" AND ")
        val direction = if (sort == "-createdAt") "DESC" else "ASC"
        val matches = jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE $where ORDER BY a.created_at $direction,a.id""", { rs, _ -> row(rs).toAnnouncement(rs.getString("author_name")) }, *args.toTypedArray())
            .filter { query.isNullOrBlank() || normalize("${it.title} ${it.body}").contains(normalize(query)) }
        val items = matches.drop((page - 1) * size).take(size)
        return Page(items, page, size, matches.size)
    }

    override fun calendar(request: ListAnnouncementCalendarQuery): Page<AnnouncementCalendarEvent> {
        val (actorId, role, sessionHash, page, size, sort, from, to, subjectId) = request
        requireReaderSession(actorId, role, sessionHash)
        val items = mutableListOf<AnnouncementCalendarEvent>()
        val announcementArgs = accessArgs(actorId, role).toMutableList<Any>()
        val announcementFilters = mutableListOf("a.withdrawn_at IS NULL", "a.occurrence IS NOT NULL", access("a", role))
        if (subjectId != null) { announcementFilters += "a.subject_id=?"; announcementArgs += subjectId }
        val occurrenceRows = jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE ${announcementFilters.joinToString(" AND ")}""", { rs, _ -> row(rs).toAnnouncement(rs.getString("author_name")) }, *announcementArgs.toTypedArray())
        occurrenceRows.forEach { announcement ->
            val occurrence = announcement.occurrence ?: return@forEach
            val date = occurrence.date
            if ((from == null || date >= from) && (to == null || date <= to)) items += calendarItem(
                announcement.id, announcement.title, occurrence.value(), announcement.body,
                announcement.subjectId, "announcement", "/avisos?id=${announcement.id}",
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
            val id = rs.getString("id")
            calendarItem(id, rs.getString("title"), AnnouncementOccurrence(local.toLocalDate().toString(), local.toLocalTime().withSecond(0).withNano(0).toString().substring(0, 5), false), rs.getString("description"), rs.getString("subject_id"), "assessment", "/questionarios?id=$id", rs.getString("kind"))
        }, *assessmentArgs.toTypedArray())
        deadlines.filter { (from == null || it.date >= from) && (to == null || it.date <= to) }.forEach(items::add)
        val direction = if (sort == "-date") -1 else 1
        val tieBreak = compareBy<AnnouncementCalendarEvent> { !it.allDay }.thenBy { it.time ?: "" }.thenBy { it.id }
        val ordered = items.sortedWith(Comparator { left, right ->
            val dateOrder = left.date.compareTo(right.date) * direction
            if (dateOrder != 0) dateOrder else tieBreak.compare(left, right)
        })
        return Page(ordered.drop((page - 1) * size).take(size), page, size, ordered.size)
    }

    override fun dashboardAnnouncements(request: ListDashboardAnnouncementStoreQuery): List<Announcement> {
        val (actorId, role, sessionHash, size) = request
        require(size in 1..10)
        requireReaderSession(actorId, role, sessionHash)
        val args = (accessArgs(actorId, role) + listOf<Any>(size)).toTypedArray()
        return jdbc.query("""SELECT a.*,u.name author_name FROM core.announcements a JOIN core.users u ON u.id=a.created_by
            WHERE a.withdrawn_at IS NULL AND ${access("a", role)}
            ORDER BY a.created_at DESC,a.id LIMIT ?""", { rs, _ -> row(rs).toAnnouncement(rs.getString("author_name")) }, *args)
    }

    override fun dashboardUpcoming(request: ListDashboardUpcomingStoreQuery): List<AnnouncementCalendarEvent> {
        val (actorId, role, sessionHash, from, to, size) = request
        require(size in 1..10)
        requireReaderSession(actorId, role, sessionHash)
        val announcementArgs = (accessArgs(actorId, role) + listOf<Any>(from, to, size)).toTypedArray()
        val announcementRows = jdbc.query("""SELECT a.* FROM core.announcements a
            WHERE a.withdrawn_at IS NULL AND a.occurrence IS NOT NULL AND ${access("a", role)}
              AND a.occurrence->>'date' BETWEEN ? AND ?
            ORDER BY a.occurrence->>'date', (a.occurrence->>'allDay')::boolean DESC, a.occurrence->>'time', a.id LIMIT ?""", { rs, _ ->
            row(rs)
        }, *announcementArgs)
        val announcements = announcementRows.mapNotNull { announcement -> announcement.occurrence?.let { occurrence ->
            calendarItem(announcement.id, announcement.title, occurrence.value(), announcement.body,
                announcement.subjectId, "announcement", "/avisos?id=${announcement.id}")
        } }

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
            val occurrence = AnnouncementOccurrence(rs.getDate("local_date").toLocalDate().toString(), rs.getString("local_time"), false)
            calendarItem(id, rs.getString("title"), occurrence, rs.getString("description"), rs.getString("subject_id"), "assessment", "/questionarios?id=$id", rs.getString("kind"))
        }, *assessmentArgs.toTypedArray())

        val order = compareBy<AnnouncementCalendarEvent> { it.date }
            .thenBy { !it.allDay }
            .thenBy { it.time ?: "" }
            .thenBy { it.id }
        return (announcements + deadlines).sortedWith(order).take(size)
    }

    private fun notify(id: String, revision: Int, draft: AnnouncementDraft) {
        val recipients = recipients(AnnouncementRecipientScope(draft.classId, draft.subjectId))
        recipients.forEach { recipient ->
            val eventId = UUID.randomUUID().toString()
            val eventType = "academic.announcement-notification-requested"
            val payload = mapper.writeValueAsBytes(AnnouncementNotificationRequested(recipient.email, id, recipient.id, revision, draft.title.trim(), draft.body))
            val event = outboxEvents.prepare(PrepareOutboxEventCommand(eventId, eventType, 1, payload))
            val inserted = jdbc.update("INSERT INTO core.announcement_notification_recipients(announcement_id,revision,recipient_id,event_id) VALUES(?,?,?,?) ON CONFLICT DO NOTHING", id, revision, recipient.id, eventId)
            if (inserted == 1) outboxEvents.append(AppendPreparedOutboxEventCommand(event, clock.instant()))
        }
    }

    private fun recipients(scope: AnnouncementRecipientScope): List<AnnouncementRecipient> = if (scope.subjectId == null) {
        jdbc.query("""SELECT DISTINCT u.id,u.email FROM core.enrollments e JOIN core.users u ON u.id=e.student_id
            JOIN core.classes c ON c.id=e.class_id WHERE e.class_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL AND c.active ORDER BY u.id""",
            { rs, _ -> AnnouncementRecipient(rs.getString(1), rs.getString(2)) }, scope.classId)
    } else {
        jdbc.query("""SELECT DISTINCT u.id,u.email FROM core.enrollments e JOIN core.users u ON u.id=e.student_id JOIN core.classes c ON c.id=e.class_id
            WHERE e.class_id=? AND e.status='active' AND u.active AND u.role='student' AND u.email_confirmed_at IS NOT NULL AND c.active
            AND EXISTS(SELECT 1 FROM core.subjects s WHERE s.id=? AND s.class_id=e.class_id) ORDER BY u.id""",
            { rs, _ -> AnnouncementRecipient(rs.getString(1), rs.getString(2)) }, scope.classId, scope.subjectId)
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
    private fun StoredAnnouncement.toAnnouncement(authorName: String, revision: Int = this.revision, updatedAt: String = this.updatedAt, updatedBy: String = this.updatedBy) = Announcement(
        id, revision, createdAt, updatedAt, updatedBy, classId, subjectId, title, body, notify, occurrence, AnnouncementAuthor(createdBy, authorName),
    )

    private fun currentDraft(row: StoredAnnouncement) = AnnouncementDraft(row.classId, row.subjectId, row.title, row.body, row.notify, row.occurrence?.value())

    private fun row(rs: ResultSet) = StoredAnnouncement(
        rs.getString("id"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant().toString(),
        rs.getTimestamp("updated_at").toInstant().toString(), rs.getString("updated_by"), rs.getString("created_by"),
        rs.getString("class_id"), rs.getString("subject_id"), rs.getString("title"), rs.getString("body"),
        rs.getBoolean("notify"), readOccurrence(rs.getString("occurrence")),
    )

    private fun readOccurrence(value: String?): AnnouncementOccurrenceProjection? = value?.let {
        val node = mapper.readTree(it)
        if (node.has("time")) AnnouncementOccurrenceProjection.WithTime(
            node.get("date").asString(),
            node.get("time")?.takeUnless { time -> time.isNull }?.asString(),
            node.get("allDay").booleanValue(),
        ) else AnnouncementOccurrenceProjection.WithoutTime(node.get("date").asString(), node.get("allDay").booleanValue())
    }

    private fun calendarItem(id: String, title: String, occurrence: AnnouncementOccurrence, description: String, subjectId: String?, source: String, href: String, kind: String = "event") = AnnouncementCalendarEvent(
        id, title, if (source == "announcement") "event" else if (kind == "quiz") "exam" else "class",
        occurrence.date, occurrence.time, occurrence.allDay, description,
        subjectId, source, id, href,
    )

    private fun audit(actorId: String, eventType: String, id: String) = jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), eventType, id, actorId)
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
    private val brasilia = ZoneId.of("America/Sao_Paulo")

    private data class StoredAnnouncement(
        val id: String,
        val revision: Int,
        val createdAt: String,
        val updatedAt: String,
        val updatedBy: String,
        val createdBy: String,
        val classId: String,
        val subjectId: String?,
        val title: String,
        val body: String,
        val notify: Boolean,
        val occurrence: AnnouncementOccurrenceProjection?,
    )
}
