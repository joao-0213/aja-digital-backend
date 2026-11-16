package br.com.ajadigital.core.forum.adapters.`in`.controllers

import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.EmptyCommandRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicCreateRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicReplyRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicRevisionRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicSolutionRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicVoteRequest
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.domain.usecases.ManageTopics
import br.com.ajadigital.core.identity.domain.usecases.AuthenticateStudent
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestId
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestIdempotencyKey
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestRevision
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
class ForumController(private val auth: AuthenticateStudent, private val topics: ManageTopics) {
    @GetMapping("/api/v1/topics")
    fun topics(@CookieValue(name = "aja_session", required = false) cookie: String?, request: HttpServletRequest, @RequestParam(defaultValue = "1") page: String, @RequestParam(defaultValue = "20") pageSize: String, @RequestParam(defaultValue = "createdAt") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) q: String?, @RequestParam(required = false) unread: String?, @RequestParam(required = false) solved: String?): Map<String, Any?> {
        val currentActor = actor(cookie)
        require(request.parameterMap.keys.all { it in setOf("page", "pageSize", "sort", "classId", "subjectId", "q", "unread", "solved") })
        fun boolean(value: String?) = when (value) { null -> null; "true" -> true; "false" -> false; else -> throw IllegalArgumentException() }
        return topics.topics(currentActor, page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, classId, subjectId, q, boolean(unread), boolean(solved))
    }
    @PostMapping("/api/v1/topics")
    fun createTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: TopicCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(topics.create(actor(cookie), TopicDraft(body.classId, body.subjectId, body.title, body.body, body.tags, body.target?.type, body.target?.id, body.attachmentId), requestIdempotencyKey(key)))
    @GetMapping("/api/v1/topics/{id}")
    fun topic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = topics.topic(actor(cookie), requestId(id))
    @PostMapping("/api/v1/topics/{id}/replies")
    @Transactional
    fun reply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: TopicReplyRequest) = ResponseEntity.status(HttpStatus.CREATED).body(topics.reply(actor(cookie), requestId(id), body.body, requestIdempotencyKey(key)))
    @PostMapping("/api/v1/topics/{id}/read")
    @Transactional
    fun readTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: EmptyCommandRequest) = topics.read(actor(cookie), requestId(id))
    @PostMapping("/api/v1/topics/{id}/solution")
    @Transactional
    fun solution(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: TopicSolutionRequest): Map<String, Any?> {
        val replyId = when { body.replyId.isNull -> null; body.replyId.isTextual -> requestId(body.replyId.asString()); else -> throw IllegalArgumentException() }
        return topics.solution(actor(cookie), requestId(id), requestRevision(body.expectedRevision), replyId)
    }
    @PostMapping("/api/v1/topics/{id}/withdraw")
    @Transactional
    fun withdrawTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: TopicRevisionRequest) = topics.withdraw(actor(cookie), requestId(id), body.expectedRevision)
    @PostMapping("/api/v1/topics/{id}/replies/{replyId}/vote")
    @Transactional
    fun voteReply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @PathVariable replyId: String, @RequestBody body: TopicVoteRequest) = topics.vote(actor(cookie), requestId(id), replyId, body.voted)
    @PostMapping("/api/v1/topics/{id}/replies/{replyId}/withdraw")
    @Transactional
    fun withdrawReply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @PathVariable replyId: String, @RequestBody body: TopicRevisionRequest) = topics.withdrawReply(actor(cookie), requestId(id), replyId, body.expectedRevision)

    private fun actor(cookie: String?): AcademicActor = auth.resolve(cookie).let { AcademicActor(it.user.id, it.user.role, it.user.active, it.user.emailConfirmedAt, it.tokenHash) }
}
