package br.com.ajadigital.core.forum.adapters.`in`.controllers
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.EmptyCommandRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicCreateRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicReplyRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicRevisionRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicSolutionRequest
import br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests.TopicVoteRequest
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.domain.models.*
import br.com.ajadigital.core.forum.application.ports.`in`.CreateTopicInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.GetTopicInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.ListTopicsInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicReadInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicSolutionInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.ReplyToTopicInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.VoteTopicReplyInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicReplyInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestId
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestIdempotencyKey
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestRevision
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
class ForumController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val createTopic: CreateTopicInputPort,
    private val listTopics: ListTopicsInputPort,
    private val getTopic: GetTopicInputPort,
    private val replyToTopic: ReplyToTopicInputPort,
    private val markTopicRead: MarkTopicReadInputPort,
    private val markTopicSolution: MarkTopicSolutionInputPort,
    private val withdrawTopic: WithdrawTopicInputPort,
    private val voteTopicReply: VoteTopicReplyInputPort,
    private val withdrawTopicReply: WithdrawTopicReplyInputPort,
) {
    @GetMapping("/api/v1/topics")
    fun topics(@CookieValue(name = "aja_session", required = false) cookie: String?, request: HttpServletRequest, @RequestParam(defaultValue = "1") page: String, @RequestParam(defaultValue = "20") pageSize: String, @RequestParam(defaultValue = "createdAt") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) subjectId: String?, @RequestParam(required = false) q: String?, @RequestParam(required = false) unread: String?, @RequestParam(required = false) solved: String?): ForumTopicPage {
        val currentActor = actor(cookie)
        require(request.parameterMap.keys.all { it in setOf("page", "pageSize", "sort", "classId", "subjectId", "q", "unread", "solved") })
        fun boolean(value: String?) = when (value) { null -> null; "true" -> true; "false" -> false; else -> throw IllegalArgumentException() }
        return listTopics.topics(ListTopicsQuery(currentActor, page.toIntOrNull() ?: throw IllegalArgumentException(), pageSize.toIntOrNull() ?: throw IllegalArgumentException(), sort, classId, subjectId, q, boolean(unread), boolean(solved)))
    }
    @PostMapping("/api/v1/topics")
    fun createTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: TopicCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createTopic.create(CreateTopicCommand(actor(cookie), TopicDraft(body.classId, body.subjectId, body.title, body.body, body.tags, body.target?.type, body.target?.id, body.attachmentId), requestIdempotencyKey(key))))
    @GetMapping("/api/v1/topics/{id}")
    fun topic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getTopic.topic(GetTopicQuery(actor(cookie), requestId(id)))
    @PostMapping("/api/v1/topics/{id}/replies")
    fun reply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: TopicReplyRequest) = ResponseEntity.status(HttpStatus.CREATED).body(replyToTopic.reply(ReplyToTopicCommand(actor(cookie), requestId(id), body.body, requestIdempotencyKey(key))))
    @PostMapping("/api/v1/topics/{id}/read")
    fun readTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: EmptyCommandRequest) = markTopicRead.read(MarkTopicReadCommand(actor(cookie), requestId(id)))
    @PostMapping("/api/v1/topics/{id}/solution")
    fun solution(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: TopicSolutionRequest): ForumTopicView {
        val replyId = when { body.replyId.isNull -> null; body.replyId.isTextual -> requestId(body.replyId.asString()); else -> throw IllegalArgumentException() }
        return markTopicSolution.solution(MarkTopicSolutionCommand(actor(cookie), requestId(id), requestRevision(body.expectedRevision), replyId))
    }
    @PostMapping("/api/v1/topics/{id}/withdraw")
    fun withdrawTopic(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: TopicRevisionRequest) = withdrawTopic.withdraw(WithdrawTopicCommand(actor(cookie), requestId(id), body.expectedRevision))
    @PostMapping("/api/v1/topics/{id}/replies/{replyId}/vote")
    fun voteReply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @PathVariable replyId: String, @RequestBody body: TopicVoteRequest) = voteTopicReply.vote(VoteTopicReplyCommand(actor(cookie), requestId(id), requestId(replyId), body.voted))
    @PostMapping("/api/v1/topics/{id}/replies/{replyId}/withdraw")
    fun withdrawReply(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @PathVariable replyId: String, @RequestBody body: TopicRevisionRequest) = withdrawTopicReply.withdrawReply(WithdrawTopicReplyCommand(actor(cookie), requestId(id), requestId(replyId), body.expectedRevision))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
}
