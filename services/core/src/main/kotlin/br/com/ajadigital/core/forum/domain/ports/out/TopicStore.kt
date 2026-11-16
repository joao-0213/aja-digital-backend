package br.com.ajadigital.core.forum.domain.ports.out
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.forum.domain.models.TopicDraft



interface TopicStore {
    fun authorizeAndFindReplay(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String): Map<String, Any?>?
    fun authorizeReply(actorId: String, role: String, sessionHash: String, id: String)
    fun create(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String): Map<String, Any?>
    fun topics(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?, unread: Boolean?, solved: Boolean?): AcademicPage
    fun dashboardTopics(actorId: String, role: String, sessionHash: String, size: Int): List<Map<String, Any?>>
    fun topic(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun reply(actorId: String, role: String, sessionHash: String, id: String, body: String): Map<String, Any?>?
    fun read(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun solution(actorId: String, role: String, sessionHash: String, id: String, revision: Int, replyId: String?): Map<String, Any?>?
    fun withdraw(actorId: String, role: String, sessionHash: String, id: String, revision: Int): Map<String, Any?>?
    fun vote(actorId: String, role: String, sessionHash: String, id: String, replyId: String, voted: Boolean): Map<String, Any?>?
    fun withdrawReply(actorId: String, role: String, sessionHash: String, id: String, replyId: String, revision: Int): Map<String, Any?>?
}
