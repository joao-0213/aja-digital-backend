package br.com.ajadigital.core.forum.adapters.out.persistence
import br.com.ajadigital.core.forum.application.models.*
import br.com.ajadigital.core.forum.domain.models.TopicDraft

import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.forum.domain.exceptions.ForumForbidden
import br.com.ajadigital.core.forum.domain.exceptions.ForumIdempotencyConflict
import br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic
import br.com.ajadigital.core.forum.application.models.ForumTopicPage
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.util.UUID

@Repository
class JdbcTopicStore(private val jdbc: JdbcTemplate, private val mapper: ObjectMapper, private val clock: Clock, private val idempotency: EncodedIdempotencyStoreOutputPort) : TopicStoreOutputPort {
    private val topicCodec = ForumTopicViewCodec(mapper)

    @Suppress("UNCHECKED_CAST")
    private fun readReceipt(value: String): Map<String, Any?> = mapper.readValue(value, Map::class.java) as Map<String, Any?>
    @Transactional
    override fun authorizeAndFindReplay(request: FindTopicReplayQuery): ForumTopicView? {
        val (actorId, role, sessionHash, draft, idempotencyKey, requestHash) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        requireWritable(draft.classId, draft.subjectId, actorId, role)
        val replay = try {
            idempotency.findEncoded(FindIdempotencyDto(actorId, "createTopic", "/topics", idempotencyKey, requestHash))?.let(::readReceipt)
        } catch (_: IdempotencyKeyReuse) {
            throw ForumIdempotencyConflict()
        }
        // Recheck after a potentially contended per-key lookup; this also rejects revocation
        // paths that mutate auth state outside the shared advisory-lock protocol.
        requireSession(actorId, role, sessionHash)
        requireWritable(draft.classId, draft.subjectId, actorId, role)
        return replay?.let(topicCodec::decode)
    }

    @Transactional
    override fun authorizeReply(request: AuthorizeTopicReplyCommand) {
        val (actorId, role, sessionHash, id) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: throw ForumNotFound()
        requireWritable(topic.classId, topic.subjectId, actorId, role)
    }

    override fun findReplyReceipt(request: br.com.ajadigital.core.forum.application.models.FindForumReplyReceiptQuery): ForumTopicView? =
        try {
            idempotency.findEncoded(FindIdempotencyDto(request.actorId, "replyToTopic", "/topics/${request.topicId}", request.key, request.requestHash))?.let(topicCodec::decodeJson)
        } catch (_: IdempotencyKeyReuse) { throw ForumIdempotencyConflict() }

    override fun saveReplyReceipt(request: br.com.ajadigital.core.forum.application.models.SaveForumReplyReceiptCommand) {
        idempotency.saveEncoded(FindIdempotencyDto(request.actorId, "replyToTopic", "/topics/${request.topicId}", request.key, request.requestHash), topicCodec.encode(request.response))
    }

    @Transactional
    override fun create(request: CreateTopicRecord): ForumTopicView {
        val (actorId, role, sessionHash, draft, idempotencyKey, requestHash) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash); requireWritable(draft.classId, draft.subjectId, actorId, role)
        try {
            idempotency.findEncoded(FindIdempotencyDto(actorId, "createTopic", "/topics", idempotencyKey, requestHash))?.let { return topicCodec.decodeJson(it) }
        } catch (_: IdempotencyKeyReuse) {
            throw ForumIdempotencyConflict()
        }
        if (draft.targetType == "material" && draft.targetId?.let { visibleMaterial(it, draft.classId, draft.subjectId, actorId, role) } != true) throw br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference()
        val file = draft.attachmentId?.let { stagedForumFile(it, actorId, draft.subjectId) }
        val now = Timestamp.from(clock.instant()); val id = UUID.randomUUID().toString()
        jdbc.update("""INSERT INTO core.topics(id,revision,created_at,updated_at,last_activity_at,updated_by,author_id,class_id,subject_id,title,body,tags,target_type,target_id,attachment_id)
            VALUES(?,1,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)""", id, now, now, now, actorId, actorId, draft.classId, draft.subjectId, draft.title.trim(), draft.body, mapper.writeValueAsString(draft.tags), draft.targetType, draft.targetId, draft.attachmentId)
        file?.let { jdbc.update("UPDATE core.files SET status='attached',expires_at=NULL WHERE id=?", draft.attachmentId) }
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?)", id, actorId, now)
        audit(actorId, "academic.topic_created", id)
        val response = topic(GetStoredTopicQuery(actorId, role, sessionHash, id)) ?: throw ForumNotFound()
        idempotency.saveEncoded(FindIdempotencyDto(actorId, "createTopic", "/topics", idempotencyKey, requestHash), topicCodec.encode(response))
        return response
    }

    override fun topics(request: ListStoredTopicsQuery): ForumTopicPage {
        val (actorId, role, sessionHash, page, size, sort, classId, subjectId, query, unread, solved) = request
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
        return ForumTopicPage(rows.mapNotNull { topic(GetStoredTopicQuery(actorId, role, sessionHash, it)) }, page, size, count)
    }

    override fun dashboardTopics(request: ListDashboardStoredTopicsQuery): List<ForumDashboardTopic> {
        val (actorId, role, sessionHash, size) = request
        require(size in 1..10)
        requireSession(actorId, role, sessionHash)
        val scope = access("t", role, actorId)
        return jdbc.query("""SELECT t.id,t.title,s.name subject_name,
            (SELECT count(*) FROM core.topic_replies r WHERE r.topic_id=t.id AND r.withdrawn_at IS NULL) reply_count
            FROM core.topics t JOIN core.subjects s ON s.id=t.subject_id
            WHERE t.withdrawn_at IS NULL AND ${scope.first}
            ORDER BY t.last_activity_at DESC,t.id LIMIT ?""", { rs, _ ->
            val id = rs.getString("id")
            ForumDashboardTopic(id, rs.getString("title"), rs.getString("subject_name"), rs.getInt("reply_count"), "/forum/topico?id=$id")
        }, *(scope.second + size).toTypedArray())
    }

    override fun topic(request: GetStoredTopicQuery): ForumTopicView? {
        val (actorId, role, sessionHash, id) = request
        requireSession(actorId, role, sessionHash)
        val (row, activity) = jdbc.query("SELECT t.*,u.name author_name FROM core.topics t JOIN core.users u ON u.id=t.author_id WHERE t.id=? AND t.withdrawn_at IS NULL AND ${access("t", role, actorId).first}", { rs, _ -> topicRow(rs) }, *(listOf(id) + access("t", role, actorId).second).toTypedArray()).firstOrNull() ?: return null
        val replies = jdbc.query("SELECT r.*,u.name author_name,(SELECT count(*) FROM core.topic_votes v WHERE v.reply_id=r.id) vote_count,EXISTS(SELECT 1 FROM core.topic_votes v WHERE v.reply_id=r.id AND v.user_id=?) voted_by_me,(t.solution_reply_id=r.id) accepted FROM core.topic_replies r JOIN core.users u ON u.id=r.author_id JOIN core.topics t ON t.id=r.topic_id WHERE r.topic_id=? AND r.withdrawn_at IS NULL ORDER BY r.created_at,r.id", { rs, _ -> replyRow(rs) }, actorId, id)
        val attachment = row.attachmentId?.let(::fileView)
        val readAt = jdbc.query("SELECT read_at FROM core.topic_reads WHERE topic_id=? AND user_id=?", { rs, _ -> rs.getTimestamp(1).toInstant() }, id, actorId).firstOrNull()
        return row.withDetails(replies, readAt != null && !readAt.isBefore(activity), attachment)
    }

    @Transactional
    override fun reply(request: CreateTopicReplyRecord): ForumTopicView? {
        val (actorId, role, sessionHash, id, body) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: return null
        requireWritable(topic.classId, topic.subjectId, actorId, role)
        val now = Timestamp.from(nextActivity(topic.lastActivityAt)); val replyId = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO core.topic_replies(id,topic_id,revision,created_at,updated_at,updated_by,author_id,body) VALUES(?,?,1,?,?,?,?,?)", replyId, id, now, now, actorId, actorId, body)
        jdbc.update("UPDATE core.topics SET updated_at=?,last_activity_at=?,updated_by=? WHERE id=?", now, now, actorId, id)
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(topic_id,user_id) DO UPDATE SET read_at=EXCLUDED.read_at", id, actorId, now)
        audit(actorId, "academic.topic_reply_created", replyId)
        return topic(GetStoredTopicQuery(actorId, role, sessionHash, id))
    }

    @Transactional
    override fun read(request: MarkTopicReadRecord): ForumTopicView? {
        val (actorId, role, sessionHash, id) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val locked = lockTopic(id, actorId, role) ?: return null
        val activity = locked.lastActivityAt
        val watermark = Timestamp.from(activity)
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(topic_id,user_id) DO UPDATE SET read_at=EXCLUDED.read_at", id, actorId, watermark)
        audit(actorId, "academic.topic_read", id)
        return topic(GetStoredTopicQuery(actorId, role, sessionHash, id))
    }

    @Transactional
    override fun solution(request: MarkTopicSolutionRecord): ForumTopicView? {
        val (actorId, role, sessionHash, id, revision, replyId) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val current = lockTopic(id, actorId, role) ?: return null
        requireWritable(current.classId, current.subjectId, actorId, role)
        if (current.authorId != actorId && role !in setOf("teacher", "admin")) throw ForumForbidden()
        if (current.revision != revision) return null
        if (replyId != null && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.topic_replies WHERE id=? AND topic_id=? AND withdrawn_at IS NULL)", Boolean::class.java, replyId, id) != true) throw ForumNotFound()
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.topics SET solution_reply_id=?,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND revision=?", replyId, now, actorId, id, revision) != 1) return null
        audit(actorId, "academic.topic_solution_changed", id)
        return topic(GetStoredTopicQuery(actorId, role, sessionHash, id))
    }

    @Transactional
    override fun withdraw(request: WithdrawTopicRecord): ForumTopicView? {
        val (actorId, role, sessionHash, id, revision) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val current = lockTopic(id, actorId, role) ?: return null
        requireWritable(current.classId, current.subjectId, actorId, role)
        if (current.authorId != actorId && role !in setOf("teacher", "admin")) throw ForumForbidden()
        if (current.revision != revision) return null
        val response = topic(GetStoredTopicQuery(actorId, role, sessionHash, id)) ?: return null
        val now = Timestamp.from(clock.instant())
        if (jdbc.update("UPDATE core.topics SET revision=revision+1,updated_at=?,updated_by=?,withdrawn_at=? WHERE id=? AND revision=? AND withdrawn_at IS NULL", now, actorId, now, id, revision) != 1) return null
        audit(actorId, "academic.topic_withdrawn", id)
        return response.revised(revision + 1, now.toInstant().toString(), actorId)
    }

    @Transactional
    override fun vote(request: VoteTopicReplyRecord): ForumTopicView? {
        val (actorId, role, sessionHash, id, replyId, voted) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: return null
        requireWritable(topic.classId, topic.subjectId, actorId, role)
        val reply = jdbc.query("SELECT author_id FROM core.topic_replies WHERE id=? AND topic_id=? AND withdrawn_at IS NULL", { rs, _ -> rs.getString(1) }, replyId, id).firstOrNull() ?: return null
        if (reply == actorId) throw ForumForbidden()
        if (voted) jdbc.update("INSERT INTO core.topic_votes(reply_id,user_id,created_at) VALUES(?,?,?) ON CONFLICT(reply_id,user_id) DO NOTHING", replyId, actorId, Timestamp.from(clock.instant()))
        else jdbc.update("DELETE FROM core.topic_votes WHERE reply_id=? AND user_id=?", replyId, actorId)
        audit(actorId, "academic.topic_vote", replyId)
        return topic(GetStoredTopicQuery(actorId, role, sessionHash, id))
    }

    @Transactional
    override fun withdrawReply(request: WithdrawTopicReplyRecord): ForumTopicView? {
        val (actorId, role, sessionHash, id, replyId, revision) = request
        lockAuthInvariant(); requireSession(actorId, role, sessionHash)
        val topic = lockTopic(id, actorId, role) ?: return null
        requireWritable(topic.classId, topic.subjectId, actorId, role)
        val reply = jdbc.query("SELECT author_id,revision FROM core.topic_replies WHERE id=? AND topic_id=? AND withdrawn_at IS NULL FOR UPDATE", { rs, _ -> rs.getString(1) to rs.getInt(2) }, replyId, id).firstOrNull() ?: return null
        if (reply.first != actorId && role !in setOf("teacher", "admin")) throw ForumForbidden()
        if (reply.second != revision) return null
        val now = Timestamp.from(clock.instant())
        jdbc.update("UPDATE core.topic_replies SET revision=revision+1,updated_at=?,updated_by=?,withdrawn_at=? WHERE id=? AND revision=?", now, actorId, now, replyId, revision)
        jdbc.update("UPDATE core.topics SET solution_reply_id=NULL,revision=revision+1,updated_at=?,updated_by=? WHERE id=? AND solution_reply_id=?", now, actorId, id, replyId)
        jdbc.update("UPDATE core.topics SET updated_at=?,updated_by=? WHERE id=?", now, actorId, id)
        jdbc.update("INSERT INTO core.topic_reads(topic_id,user_id,read_at) VALUES(?,?,?) ON CONFLICT(topic_id,user_id) DO UPDATE SET read_at=EXCLUDED.read_at", id, actorId, Timestamp.from(topic.lastActivityAt))
        audit(actorId, "academic.topic_reply_withdrawn", replyId)
        return topic(GetStoredTopicQuery(actorId, role, sessionHash, id))
    }

    private fun lockTopic(id: String, actorId: String, role: String): ForumTopicLock? {
        val clause = access("t", role, actorId)
        return jdbc.query("SELECT t.id,t.revision,t.author_id,t.class_id,t.subject_id,t.solution_reply_id,t.last_activity_at FROM core.topics t WHERE t.id=? AND t.withdrawn_at IS NULL AND ${clause.first} FOR UPDATE", { rs, _ -> ForumTopicLock(rs.getString("id"), rs.getInt("revision"), rs.getString("author_id"), rs.getString("class_id"), rs.getString("subject_id"), rs.getString("solution_reply_id"), rs.getTimestamp("last_activity_at").toInstant()) }, *(listOf(id) + clause.second).toTypedArray()).firstOrNull()
    }

    private fun requireWritable(classId: String, subjectId: String, actorId: String, role: String) {
        val active = jdbc.query("SELECT c.active FROM core.classes c JOIN core.subjects s ON s.class_id=c.id WHERE c.id=? AND s.id=?", { rs, _ -> rs.getBoolean(1) }, classId, subjectId).firstOrNull() ?: throw br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound()
        val allowed = when (role) {
            "admin" -> true
            "teacher" -> jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.subject_teachers st WHERE st.subject_id=? AND st.teacher_id=?)", Boolean::class.java, subjectId, actorId) == true
            "student" -> active && jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM core.enrollments WHERE class_id=? AND student_id=? AND status='active')", Boolean::class.java, classId, actorId) == true
            else -> false
        }
        if (!allowed) throw br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound()
        if (!active) throw ForumForbidden()
    }

    private fun visibleMaterial(id: String, classId: String, subjectId: String, actorId: String, role: String) = jdbc.queryForObject("""SELECT EXISTS(SELECT 1 FROM core.materials mt JOIN core.modules m ON m.id=mt.module_id AND m.withdrawn_at IS NULL JOIN core.subjects s ON s.id=m.subject_id JOIN core.classes c ON c.id=s.class_id WHERE mt.id=? AND s.id=? AND c.id=? AND ${subjectAccess("s", role)})""", Boolean::class.java, *(listOf(id, subjectId, classId) + if (role == "teacher" || role == "student") listOf(actorId) else emptyList()).toTypedArray()) == true

    private fun stagedForumFile(id: String, actorId: String, subjectId: String) = jdbc.query("SELECT id FROM core.files WHERE id=? AND owner_id=? AND purpose='forum' AND parent_id=? AND status='staged' AND expires_at>? FOR UPDATE", { rs, _ -> rs.getString(1) }, id, actorId, subjectId, Timestamp.from(clock.instant())).firstOrNull() ?: throw ForumNotFound()
    private fun fileView(id: String): ForumAttachmentView? = jdbc.query("SELECT id,name,size,mime,purpose,parent_id,status FROM core.files WHERE id=? AND status='attached'", { rs, _ -> ForumAttachmentView(rs.getString("id"), rs.getString("name"), rs.getLong("size"), rs.getString("mime"), rs.getString("purpose"), rs.getString("parent_id"), rs.getString("status"), "/api/v1/files/${rs.getString("id")}/content") }, id).firstOrNull()
    private fun topicRow(rs: ResultSet): Pair<ForumTopicView, java.time.Instant> {
        val targetType = rs.getString("target_type")
        val targetId = rs.getString("target_id")
        val target = when {
            targetType == null && targetId == null -> null
            targetType != null && targetId != null -> ForumTargetView(targetType, targetId)
            else -> throw IllegalStateException("Stored forum target is incomplete")
        }
        val topic = ForumTopicView(rs.getString("id"), rs.getInt("revision"), rs.getTimestamp("created_at").toInstant().toString(),
            rs.getTimestamp("updated_at").toInstant().toString(), rs.getString("updated_by"), rs.getString("class_id"),
            rs.getString("subject_id"), rs.getString("title"), rs.getString("body"),
            mapper.readValue(rs.getString("tags"), Array<String>::class.java).toList(), target, rs.getString("attachment_id"),
            ForumAuthorView(rs.getString("author_id"), rs.getString("author_name")), emptyList(), false, null,
            targetId, null)
        return topic to rs.getTimestamp("last_activity_at").toInstant()
    }
    private fun replyRow(rs: ResultSet) = ForumReplyView(rs.getString("id"), rs.getInt("revision"),
        rs.getTimestamp("created_at").toInstant().toString(), rs.getTimestamp("updated_at").toInstant().toString(),
        rs.getString("updated_by"), ForumAuthorView(rs.getString("author_id"), rs.getString("author_name")),
        rs.getString("body"), rs.getInt("vote_count"), rs.getBoolean("voted_by_me"), rs.getBoolean("accepted"))
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
        val role = jdbc.query("SELECT u.role FROM core.users u JOIN core.sessions s ON s.user_id=u.id WHERE u.id=? AND u.active AND (u.role<>'student' OR u.email_confirmed_at IS NOT NULL) AND s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?", { rs, _ -> rs.getString(1) }, actorId, hash, Timestamp.from(clock.instant())).firstOrNull() ?: throw ForumForbidden()
        if (role != expectedRole || role !in setOf("admin", "teacher", "student")) throw ForumForbidden()
    }
    private fun lockAuthInvariant() { jdbc.queryForObject("SELECT pg_advisory_xact_lock(812734) IS NULL", Boolean::class.java) }
    private fun nextActivity(previous: java.time.Instant) = maxOf(clock.instant(), previous.plusNanos(1_000))
    private fun audit(actorId: String, event: String, resourceId: String) { jdbc.update("INSERT INTO core.audit_events(id,event_type,outcome,subject_user_id,actor_user_id) VALUES(?,?,'success',?,?)", UUID.randomUUID().toString(), event, resourceId, actorId) }
}
