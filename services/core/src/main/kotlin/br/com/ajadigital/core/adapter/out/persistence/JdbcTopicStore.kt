package br.com.ajadigital.core.adapter.out.persistence

import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicIdempotencyConflict
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.ports.out.IdempotencyStore
import br.com.ajadigital.core.ports.out.AcademicPage
import br.com.ajadigital.core.ports.out.TopicDraft
import br.com.ajadigital.core.ports.out.TopicStore
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcTopicStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val clock: Clock, private val idempotency: IdempotencyStore) : TopicStore {
    @Transactional
    override fun authorizeAndFindReplay(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        requireWritable(draft.classId, draft.subjectId, actorId, role)
        val replay = try {
            idempotency.find(actorId, "createTopic", "/topics", idempotencyKey, requestHash)
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        // Recheck after a potentially contended per-key lookup; this also rejects revocation
        // paths that mutate auth state outside the shared advisory-lock protocol.
        requireSession(actorId, role, sessionHash)
        requireWritable(draft.classId, draft.subjectId, actorId, role)
        return replay
    }

    @Transactional
    override fun authorizeReply(actorId: String, role: String, sessionHash: String, id: String) {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: throw AcademicNotFound()
        requireWritable(topic["classId"] as String, topic["subjectId"] as String, actorId, role)
    }

    @Transactional
    override fun create(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String): Map<String, Any?> {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash); requireWritable(draft.classId, draft.subjectId, actorId, role)
        try {
            idempotency.find(actorId, "createTopic", "/topics", idempotencyKey, requestHash)?.let { return it }
        } catch (_: IdempotencyKeyReuse) {
            throw AcademicIdempotencyConflict()
        }
        if (draft.targetType == "material" && !visibleMaterial(draft.targetId!!, draft.classId, draft.subjectId, actorId, role)) throw br.com.ajadigital.core.application.academic.InvalidTopicReference()
        val file = draft.attachmentId?.let { stagedForumFile(it, actorId, draft.subjectId) }
        val now = Timestamp.from(clock.instant()); val id = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.topics(id,revision,created_at,updated_at,last_activity_at,updated_by,author_id,class_id,subject_id,title,body,tags,target_type,target_id,attachment_id)
            VALUES(?,1,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)""", id, now, now, now, actorId, actorId, draft.classId, draft.subjectId, draft.title.trim(), draft.body, mapper.writeValueAsString(draft.tags), draft.targetType, draft.targetId, draft.attachmentId)
        file?.let { jdbc.update("UPDATE core.files SET status='attached',expires_at=NULL WHERE id=?", draft.attachmentId) }
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?)", id, actorId, now)
        audit(actorId, "academic.topic_created", id)
        val response = topic(actorId, role, sessionHash, id) ?: throw AcademicNotFound()
        idempotency.save(actorId, "createTopic", "/topics", idempotencyKey, requestHash, response)
        return response
    }

    override fun topics(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?, unread: Boolean?, solved: Boolean?): AcademicPage {
        requireSession(actorId, role, sessionHash)
        val access = access("t", role, actorId)
        val clauses = mutableListOf("t.withdrawn_at IS NULL", access.first)
        val args = mutableListOf<Any>().apply { addAll(access.second) }
        classId?.let { clauses += "t.class_id=?"; args += it }
        subjectId?.let { clauses += "t.subject_id=?"; args += it }
        query?.let { q ->
            clauses += "(position(core.fold_forum(?) in core.fold_forum(t.title))>0 OR position(core.fold_forum(?) in core.fold_forum(t.body))>0 OR EXISTS(SELECT 1 FROM core.topic_replies r WHERE r.topic_id=t.id AND r.withdrawn_at IS NULL AND position(core.fold_forum(?) in core.fold_forum(r.body))>0))"
            args += q; args += q; args += q
        }
        if (unread != null) clauses += if (unread) "NOT EXISTS(SELECT 1 FROM core.topic_reads tr WHERE tr.topic_id=t.id AND tr.user_id=? AND tr.read_at>=t.last_activity_at)" else "EXISTS(SELECT 1 FROM core.topic_reads tr WHERE tr.topic_id=t.id AND tr.user_id=? AND tr.read_at>=t.last_activity_at)"
        if (unread != null) args += actorId
        if (solved != null) clauses += if (solved) "t.solution_reply_id IS NOT NULL" else "t.solution_reply_id IS NULL"
        val where = clauses.joinToString(" AND ")
        val count = jdbc.queryForObject("SELECT count(*) FROM core.topics t WHERE $where", Int::class.java, *args.toTypedArray()) ?: 0
        val direction = if (sort == "-createdAt") "DESC" else "ASC"
        val rows = jdbc.query("SELECT t.id FROM core.topics t WHERE $where ORDER BY t.created_at $direction,t.id ASC LIMIT ? OFFSET ?", { rs, _ -> rs.getString(1) }, *(args + listOf(size, (page - 1) * size)).toTypedArray())
        return AcademicPage(rows.mapNotNull { topic(actorId, role, sessionHash, it) }, page, size, count)
    }

    override fun dashboardTopics(actorId: String, role: String, sessionHash: String, size: Int): List<Map<String, Any?>> {
        require(size in 1..10)
        requireSession(actorId, role, sessionHash)
        val scope = access("t", role, actorId)
        return jdbc.query("""SELECT t.id,t.title,s.name subject_name,
            (SELECT count(*) FROM core.topic_replies r WHERE r.topic_id=t.id AND r.withdrawn_at IS NULL) reply_count
            FROM core.topics t JOIN core.subjects s ON s.id=t.subject_id
            WHERE t.withdrawn_at IS NULL AND ${scope.first}
            ORDER BY t.last_activity_at DESC,t.id LIMIT ?""", { rs, _ ->
            val id = rs.getString("id")
            mapOf("id" to id, "title" to rs.getString("title"), "subjectName" to rs.getString("subject_name"), "replyCount" to rs.getInt("reply_count"), "href" to "/forum/topico?id=$id")
        }, *(scope.second + size).toTypedArray())
    }

    override fun topic(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        requireSession(actorId, role, sessionHash)
        val row = jdbc.query("SELECT t.*,u.name author_name FROM core.topics t JOIN core.users u ON u.id=t.author_id WHERE t.id=? AND t.withdrawn_at IS NULL AND ${access("t", role, actorId).first}", { rs, _ -> topicRow(rs) }, *(listOf(id) + access("t", role, actorId).second).toTypedArray()).firstOrNull() ?: return null
        val replies = jdbc.query("SELECT r.*,u.name author_name,(SELECT count(*) FROM core.topic_votes v WHERE v.reply_id=r.id) vote_count,EXISTS(SELECT 1 FROM core.topic_votes v WHERE v.reply_id=r.id AND v.user_id=?) voted_by_me,(t.solution_reply_id=r.id) accepted FROM core.topic_replies r JOIN core.users u ON u.id=r.author_id JOIN core.topics t ON t.id=r.topic_id WHERE r.topic_id=? AND r.withdrawn_at IS NULL ORDER BY r.created_at,r.id", { rs, _ -> replyMap(rs) }, actorId, id)
        val attachment = row["attachmentId"]?.let { fileMap(it as String) }
        val target = row["targetType"]?.let { mapOf("type" to it, "id" to row["targetId"]) }
        val readAt = jdbc.query("SELECT read_at FROM core.topic_reads WHERE topic_id=? AND user_id=?", { rs, _ -> rs.getTimestamp(1).toInstant() }, id, actorId).firstOrNull()
        val updated = row["updatedAt"] as java.time.Instant
        val activity = row["lastActivityAt"] as java.time.Instant
        return mapOf("id" to row["id"], "revision" to row["revision"], "createdAt" to (row["createdAt"] as java.time.Instant).toString(), "updatedAt" to updated.toString(), "updatedBy" to row["updatedBy"], "classId" to row["classId"], "subjectId" to row["subjectId"], "title" to row["title"], "body" to row["body"], "tags" to mapper.readValue(row["tags"] as String, List::class.java), "target" to target, "attachmentId" to row["attachmentId"], "author" to mapOf("id" to row["authorId"], "name" to row["authorName"]), "replies" to replies, "read" to (readAt != null && !readAt.isBefore(activity)), "attachment" to attachment, "legacyReference" to row["targetId"], "legacyAttachmentName" to attachment?.get("name"))
    }

    @Transactional
    override fun reply(actorId: String, role: String, sessionHash: String, id: String, body: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: return null
        requireWritable(topic["classId"] as String, topic["subjectId"] as String, actorId, role)
        val now = Timestamp.from(nextActivity(topic["lastActivityAt"] as java.time.Instant)); val replyId = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO core.topic_replies(id,topic_id,revision,created_at,updated_at,updated_by,author_id,body) VALUES(?,?,1,?,?,?,?,?)", replyId, id, now, now, actorId, actorId, body)
        jdbc.update("UPDATE core.topics SET updated_at=?,last_activity_at=?,updated_by=? WHERE id=?", now, now, actorId, id)
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(topic_id,user_id) DO UPDATE SET read_at=EXCLUDED.read_at", id, actorId, now)
        audit(actorId, "academic.topic_reply_created", replyId)
        return topic(actorId, role, sessionHash, id)
    }

    @Transactional
    override fun read(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val locked = lockTopic(id, actorId, role) ?: return null
        val activity = locked["lastActivityAt"] as java.time.Instant
        val watermark = Timestamp.from(activity)
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(topic_id,user_id) DO UPDATE SET read_at=EXCLUDED.read_at", id, actorId, watermark)
        audit(actorId, "academic.topic_read", id)
        return topic(actorId, role, sessionHash, id)
    }

    @Transactional
    override fun solution(actorId: String, role: String, sessionHash: String, id: String, revision: Int, replyId: String?): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val current = lockTopic(id, actorId, role) ?: return null
        requireWritable(current["classId"] as String, current["subjectId"] as String, actorId, role)
        if (current["authorId"] != actorId && role !in setOf("teacher", "admin")) throw AcademicForbidden()
        if (current["revision"] != revision) return null
        if (replyId != null && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.topic_replies WHERE id=? AND topic_id=? AND withdrawn_at IS NULL)", Boolean::class.java, replyId, id) != true) throw AcademicNotFound()
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.topics SET solution_reply_id=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", replyId, now, actorId, id, revision) != 1) return null
        audit(actorId, "academic.topic_solution_changed", id)
        return topic(actorId, role, sessionHash, id)
    }

    @Transactional
    override fun withdraw(actorId: String, role: String, sessionHash: String, id: String, revision: Int): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val current = lockTopic(id, actorId, role) ?: return null
        requireWritable(current["classId"] as String, current["subjectId"] as String, actorId, role)
        if (current["authorId"] != actorId && role !in setOf("teacher", "admin")) throw AcademicForbidden()
        if (current["revision"] != revision) return null
        val response = topic(actorId, role, sessionHash, id) ?: return null
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.topics SET revision=revision+1,updated_at=?,updated_by=?,withdrawn_at=? WHERE id=? AND revision=? AND withdrawn_at IS NULL", now, actorId, now, id, revision) != 1) return null
        audit(actorId, "academic.topic_withdrawn", id)
        return response + mapOf("revision" to revision + 1, "updatedAt" to now.toInstant().toString(), "updatedBy" to actorId)
    }

    @Transactional
    override fun vote(actorId: String, role: String, sessionHash: String, id: String, replyId: String, voted: Boolean): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: return null
        requireWritable(topic["classId"] as String, topic["subjectId"] as String, actorId, role)
        val reply = jdbc.query("SELECT author_id FROM core.topic_replies WHERE id=? AND topic_id=? AND withdrawn_at IS NULL", { rs, _ -> rs.getString(1) }, replyId, id).firstOrNull() ?: return null
        if (reply == actorId) throw AcademicForbidden()
        if (voted) jdbc.update("INSERT INTO core.topic_votes(reply_id,user_id,created_at) VALUES(?,?,?) ON CONFLICT(reply_id,user_id) DO NOTHING", replyId, actorId, Timestamp.from(clock.instant()))
        else jdbc.update("DELETE FROM core.topic_votes WHERE reply_id=? AND user_id=?", replyId, actorId)
        audit(actorId, "academic.topic_vote", replyId)
        return topic(actorId, role, sessionHash, id)
    }

    @Transactional
    override fun withdrawReply(actorId: String, role: String, sessionHash: String, id: String, replyId: String, revision: Int): Map<String, Any?>? {
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: return null
        requireWritable(topic["classId"] as String, topic["subjectId"] as String, actorId, role)
        val reply = jdbc.query("SELECT author_id,revision FROM core.topic_replies WHERE id=? AND topic_id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getInt(2) }, replyId, id).firstOrNull() ?: return null
        if (reply.first != actorId && role !in setOf("teacher", "admin")) throw AcademicForbidden()
        if (reply.second != revision) return null
        val now = Timestamp.from(clock.instant())
        jdbc.update("UPDATE core.topic_replies SET revision=revision+1,updated_at=?,updated_by=?,withdrawn_at=? WHERE id=? AND revision=?", now, actorId, now, replyId, revision)
        jdbc.update("UPDATE core.topics SET solution_reply_id=NULL,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND solution_reply_id=?", now, actorId, id, replyId)
        jdbc.update("UPDATE core.topics SET updated_at=?,updated_by=? WHERE id=?", now, actorId, id)
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(topic_id,user_id) DO UPDATE SET read_at=EXCLUDED.read_at", id, actorId, Timestamp.from(topic["lastActivityAt"] as java.time.Instant))
        audit(actorId, "academic.topic_reply_withdrawn", replyId)
        return topic(actorId, role, sessionHash, id)
    }

    private fun lockTopic(id: String, actorId: String, role: String): Map<String, Any?>? {
        val clause = access("t", role, actorId)
        return jdbc.query("SELECT t.id,t.revision,t.author_id,t.class_id,t.subject_id,t.solution_reply_id,t.last_activity_at FROM core.topics t WHERE t.id=? AND t.withdrawn_at IS NULL AND ${clause.first} FOR UPDATE", { rs, _ -> mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "authorId" to rs.getString("author_id"), "classId" to rs.getString("class_id"), "subjectId" to rs.getString("subject_id"), "solutionReplyId" to rs.getString("solution_reply_id"), "lastActivityAt" to rs.getTimestamp("last_activity_at").toInstant()) }, *(listOf(id) + clause.second).toTypedArray()).firstOrNull()
    }

    private fun requireWritable(classId: String, subjectId: String, actorId: String, role: String) {
        val active = jdbc.query("SELECT c.active FROM core.classes c JOIN core.subjects s ON s.class_id=c.id WHERE c.id=? AND s.id=?", { rs, _ -> rs.getBoolean(1) }, classId, subjectId).firstOrNull() ?: throw br.com.ajadigital.core.application.academic.AcademicNotFound()
        val allowed = when (role) {
            "admin" -> true
            "teacher" -> jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st WHERE st.subject_id=? AND st.teacher_id=?)", Boolean::class.java, subjectId, actorId) == true
            "student" -> active && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.enrollments WHERE class_id=? AND student_id=? AND status='active')", Boolean::class.java, classId, actorId) == true
            else -> false
        }
        if (!allowed) throw br.com.ajadigital.core.application.academic.AcademicNotFound()
        if (!active) throw AcademicForbidden()
    }

    private fun visibleMaterial(id: String, classId: String, subjectId: String, actorId: String, role: String) = jdbc.queryForObject("""SELECT EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id WHERE mt.id=? AND s.id=? AND c.id=? AND ${subjectAccess("s", role)})""", Boolean::class.java, *(listOf(id, subjectId, classId) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()) == true

    private fun stagedForumFile(id: String, actorId: String, subjectId: String) = jdbc.query("SELECT id FROM core.files WHERE id=? AND owner_id=? AND purpose='forum' AND parent_id=? AND status='staged' AND expires_at>? FOR UPDATE", { rs, _ -> rs.getString(1) }, id, actorId, subjectId, Timestamp.from(clock.instant())).firstOrNull() ?: throw AcademicNotFound()
    private fun fileMap(id: String): Map<String, Any?>? = jdbc.query("SELECT id,name,size,mime,purpose,parent_id,status FROM core.files WHERE id=? AND status='attached'", { rs, _ -> mapOf("id" to rs.getString("id"), "name" to rs.getString("name"), "size" to rs.getLong("size"), "mime" to rs.getString("mime"), "purpose" to rs.getString("purpose"), "parentId" to rs.getString("parent_id"), "status" to rs.getString("status"), "downloadUrl" to "/api/v1/files/${rs.getString("id")}/content") }, id).firstOrNull()
    private fun topicRow(rs: ResultSet) = mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant(), "updatedAt" to rs.getTimestamp("updated_at").toInstant(), "lastActivityAt" to rs.getTimestamp("last_activity_at").toInstant(), "updatedBy" to rs.getString("updated_by"), "authorId" to rs.getString("author_id"), "authorName" to rs.getString("author_name"), "classId" to rs.getString("class_id"), "subjectId" to rs.getString("subject_id"), "title" to rs.getString("title"), "body" to rs.getString("body"), "tags" to rs.getString("tags"), "targetType" to rs.getString("target_type"), "targetId" to rs.getString("target_id"), "attachmentId" to rs.getString("attachment_id"))
    private fun replyMap(rs: ResultSet) = mapOf("id" to rs.getString("id"), "revision" to rs.getInt("revision"), "createdAt" to rs.getTimestamp("created_at").toInstant().toString(), "updatedAt" to rs.getTimestamp("updated_at").toInstant().toString(), "updatedBy" to rs.getString("updated_by"), "author" to mapOf("id" to rs.getString("author_id"), "name" to rs.getString("author_name")), "body" to rs.getString("body"), "voteCount" to rs.getInt("vote_count"), "votedByMe" to rs.getBoolean("voted_by_me"), "accepted" to rs.getBoolean("accepted"))
    private fun access(alias: String, role: String, actorId: String): Pair<String, List<Any>> = when (role) {
        "admin" -> "true" to emptyList()
        "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=$alias.subject_id AND st.teacher_id=? AND u.active AND u.role='teacher')" to listOf(actorId)
        "student" -> "EXISTS(SELECT 1 FROM core.classes c JOIN core.enrollments e ON e.class_id=c.id JOIN core.users u ON u.id=e.student_id WHERE c.id=$alias.class_id AND c.active AND e.student_id=? AND e.status='active' AND u.active AND u.email_confirmed_at IS NOT NULL)" to listOf(actorId)
        else -> "false" to emptyList()
    }
    private fun subjectAccess(subject: String, role: String) = when (role) {
        "admin" -> "true"
        "teacher" -> "EXISTS(SELECT 1 FROM core.subject_teachers st JOIN core.users u ON u.id=st.teacher_id WHERE st.subject_id=$subject.id AND st.teacher_id=? AND u.active AND u.role='teacher')"
        "student" -> "EXISTS(SELECT 1 FROM core.enrollments e JOIN core.users u ON u.id=e.student_id WHERE e.class_id=$subject.class_id AND e.status='active' AND e.student_id=? AND u.active AND u.email_confirmed_at IS NOT NULL)"
        else -> "false"
    }
    private fun requireSession(actorId: String, expectedRole: String, hash: String) {
        val role = jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND (u.role<>'student' OR u.email_confirmed_at IS NOT NULL) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, actorId, hash, Timestamp.from(clock.instant())).firstOrNull() ?: throw AcademicForbidden()
        if (role != expectedRole || role !in setOf("admin", "teacher", "student")) throw AcademicForbidden()
    }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun nextActivity(previous: java.time.Instant) = maxOf(clock.instant(), previous.plusNanos(1_000))
    private fun audit(actorId: String, event: String, resourceId: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), event, resourceId, actorId) }
}
