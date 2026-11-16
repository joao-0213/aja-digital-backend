package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.models.TopicDraft

interface ManageTopicsUseCase {
    fun create(actor: AcademicActor, draft: TopicDraft, key: String): Map<String, Any?>
    fun topics(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?, unread: Boolean?, solved: Boolean?): Map<String, Any?>
    fun dashboardTopics(actor: AcademicActor): List<Map<String, Any?>>
    fun topic(actor: AcademicActor, id: String): Map<String, Any?>
    fun reply(actor: AcademicActor, id: String, body: String, key: String): Map<String, Any?>
    fun read(actor: AcademicActor, id: String): Map<String, Any?>
    fun solution(actor: AcademicActor, id: String, revision: Int, replyId: String?): Map<String, Any?>
    fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?>
    fun vote(actor: AcademicActor, id: String, replyId: String, voted: Boolean): Map<String, Any?>
    fun withdrawReply(actor: AcademicActor, id: String, replyId: String, revision: Int): Map<String, Any?>
}
