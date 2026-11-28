package br.com.ajadigital.core.forum
import br.com.ajadigital.core.forum.application.models.*
import br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic

import br.com.ajadigital.core.forum.domain.exceptions.ForumForbidden
import br.com.ajadigital.core.forum.application.models.*
import br.com.ajadigital.core.forum.application.ports.out.ForumQuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.usecases.CreateTopicUseCase
import br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshot
import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshotQuery
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.adapters.out.serialization.JacksonForumRequestFingerprint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class ForumApplicationUseCaseTests {
    private val fingerprints = JacksonForumRequestFingerprint(JsonMapper.builder().build())
    private val actor = AuthenticatedActor("student-1", "student", true, Instant.parse("2026-01-01T00:00:00Z"), "session-hash")
    private val draft = TopicDraft("class-1", "subject-1", "Title", "Body", emptyList(), "question", "q-1", null)
    private val request = CreateTopicCommand(actor, draft, "idempotency-123")

    @Test
    fun `authorization precedes replay lookup and remote snapshot`() {
        var storeCalls = 0
        var providerCalls = 0
        val store = object : StubTopicStore() {
            override fun authorizeAndFindReplay(request: FindTopicReplayQuery): ForumTopicView? { storeCalls++; return null }
        }
        val snapshots = object : ForumQuestionSnapshotProviderOutputPort {
            override fun snapshots(request: ForumQuestionSnapshotQuery): List<ForumQuestionSnapshot> { providerCalls++; return emptyList() }
        }
        val denied = CreateTopicUseCase(store, snapshots, fingerprints)

        assertThrows(ForumForbidden::class.java) {
            denied.create(request.copy(actor = AuthenticatedActor("student-1", "student", false, actor.emailConfirmedAt, "session-hash")))
        }
        assertEquals(0, storeCalls)
        assertEquals(0, providerCalls)
    }

    @Test
    fun `an authorized replay skips the remote snapshot call`() {
        var providerCalls = 0
        val replay = topic()
        val store = object : StubTopicStore() {
            override fun authorizeAndFindReplay(request: FindTopicReplayQuery) = replay
        }
        val snapshots = object : ForumQuestionSnapshotProviderOutputPort {
            override fun snapshots(request: ForumQuestionSnapshotQuery): List<ForumQuestionSnapshot> { providerCalls++; return emptyList() }
        }

        assertEquals(replay.id, CreateTopicUseCase(store, snapshots, fingerprints).create(request).id)
        assertEquals(0, providerCalls)
    }

    @Test
    fun `question reference result must contain exactly the requested id`() {
        val snapshots = object : ForumQuestionSnapshotProviderOutputPort {
            override fun snapshots(request: ForumQuestionSnapshotQuery) = listOf(ForumQuestionSnapshot("different"))
        }

        assertThrows(InvalidTopicReference::class.java) {
            CreateTopicUseCase(StubTopicStore(), snapshots, fingerprints).create(request)
        }
    }

    @Test
    fun `topic snapshots tags and replies at the application boundary`() {
        val tags = mutableListOf("help")
        val replies = mutableListOf(ForumReplyView("reply-1", 1, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null,
            ForumAuthorView("teacher-1", "Teacher"), "Answer", 0, false, false))
        val view = ForumTopicView("topic-1", 1, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null,
            "class-1", "subject-1", "Title", "Body", tags, null, null, ForumAuthorView("student-1", "Student"),
            replies, true, null, null, null)

        tags += "other"
        replies.clear()
        assertEquals(listOf("help"), view.tags)
        assertEquals(listOf("reply-1"), view.replies.map(ForumReplyView::id))
    }

    private fun topic() = ForumTopicView("topic-1", 1, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null,
        "class-1", "subject-1", "Title", "Body", emptyList(), null, null, ForumAuthorView("student-1", "Student"),
        emptyList(), true, null, null, null)

    private open class StubTopicStore : TopicStoreOutputPort {
        override fun authorizeAndFindReplay(request: FindTopicReplayQuery): ForumTopicView? = null
        override fun authorizeReply(request: AuthorizeTopicReplyCommand) = Unit
        override fun findReplyReceipt(request: FindForumReplyReceiptQuery): ForumTopicView? = null
        override fun saveReplyReceipt(request: SaveForumReplyReceiptCommand) = Unit
        override fun create(request: CreateTopicRecord): ForumTopicView = error("unexpected")
        override fun topics(request: ListStoredTopicsQuery) = ForumTopicPage(emptyList(), 1, 20, 0)
        override fun dashboardTopics(request: ListDashboardStoredTopicsQuery) = emptyList<ForumDashboardTopic>()
        override fun topic(request: GetStoredTopicQuery): ForumTopicView? = null
        override fun reply(request: CreateTopicReplyRecord): ForumTopicView? = null
        override fun read(request: MarkTopicReadRecord): ForumTopicView? = null
        override fun solution(request: MarkTopicSolutionRecord): ForumTopicView? = null
        override fun withdraw(request: WithdrawTopicRecord): ForumTopicView? = null
        override fun vote(request: VoteTopicReplyRecord): ForumTopicView? = null
        override fun withdrawReply(request: WithdrawTopicReplyRecord): ForumTopicView? = null
    }
}
