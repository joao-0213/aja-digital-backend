package br.com.ajadigital.core.shared.adapters.out.transaction
import br.com.ajadigital.core.forum.application.models.*
import br.com.ajadigital.core.forum.application.contracts.ForumDashboardTopic

import br.com.ajadigital.core.assessment.domain.models.AssessmentKind
import br.com.ajadigital.core.forum.application.models.*
import br.com.ajadigital.core.shared.application.contracts.FieldChange
import br.com.ajadigital.core.assessment.application.models.AssessmentReceiptKey
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.assessment.application.contracts.DashboardAssessment
import br.com.ajadigital.core.assessment.application.contracts.ListDashboardAssessmentsQuery
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.application.models.AssessmentChanges
import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.AssessmentQuestionSelections
import br.com.ajadigital.core.assessment.application.models.AssessmentResults
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentRecord
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentCommand
import br.com.ajadigital.core.assessment.application.models.PublishAssessmentCommand
import br.com.ajadigital.core.assessment.application.models.QuestionSnapshotQuery
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentCommand
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentRecord
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentWriteOutputPort
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.assessment.application.usecases.CreateAssessmentUseCase
import br.com.ajadigital.core.assessment.application.usecases.UpdateAssessmentUseCase
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.ports.out.ForumQuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.forum.application.usecases.CreateTopicUseCase
import br.com.ajadigital.core.forum.application.usecases.ReplyToTopicUseCase
import br.com.ajadigital.core.materials.adapters.out.serialization.JacksonMaterialPublicationReceipt
import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.models.*
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.application.usecases.PublishMaterialsUseCase
import br.com.ajadigital.core.shared.application.contracts.Page
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import br.com.ajadigital.core.forum.adapters.out.serialization.JacksonForumRequestFingerprint
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.toRequestValue
import java.time.Instant

class SpringTransactionRunnerTests {
    private val mapper = JsonMapper.builder().build()
    private val fingerprints = JacksonForumRequestFingerprint(mapper)
    private val teacher = AuthenticatedActor("teacher-1", "teacher", true, Instant.parse("2026-01-01T00:00:00Z"), "hash-1")
    private fun forumTopic(id: String) = ForumTopicView(id, 1, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null, "class-1", "sub-1", "Title", "Body", emptyList(), null, null, ForumAuthorView("teacher-1", "Teacher"), emptyList(), true, null, null, null)

    @Test
    fun `ManageTopics create calls question snapshots outside TransactionRunnerOutputPort`() {
        var inTx = false
        var snapshotInTx: Boolean? = null
        var txEntries = 0
        val runner = object : TransactionRunnerOutputPort {
            override fun <T> run(block: () -> T): T {
                txEntries++
                inTx = true
                return try { block() } finally { inTx = false }
            }
        }
        val snapshots = object : ForumQuestionSnapshotProviderOutputPort {
            override fun snapshots(request: ForumQuestionSnapshotQuery): List<ForumQuestionSnapshot> { snapshotInTx = inTx; return request.ids.map(::ForumQuestionSnapshot) }
        }
        val topic = ForumTopicView("topic-1", 1, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null, "class-1", "sub-1", "Title", "Body", emptyList(), null, null, ForumAuthorView("teacher-1", "Teacher"), emptyList(), true, null, null, null)
        val store = object : TopicStoreOutputPort {
            override fun authorizeAndFindReplay(request: FindTopicReplayQuery): ForumTopicView? = null
            override fun authorizeReply(request: AuthorizeTopicReplyCommand) {}
            override fun findReplyReceipt(request: FindForumReplyReceiptQuery): ForumTopicView? = null
            override fun saveReplyReceipt(request: SaveForumReplyReceiptCommand) = Unit
            override fun create(request: CreateTopicRecord) = topic
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
        val useCase = CreateTopicUseCase(store, snapshots, fingerprints)
        val result = useCase.create(CreateTopicCommand(teacher, TopicDraft("class-1", "sub-1", "Title", "Body", emptyList(), "question", "q-1", null), "idem-key-1234"))

        assertEquals("topic-1", result.id)
        assertEquals(false, snapshotInTx)
        assertEquals(0, txEntries)
    }

    @Test
    fun `assessment create and update capture questions before the atomic write`() {
        val calls = mutableListOf<String>()
        val snapshots = object : QuestionSnapshotProviderOutputPort {
            override fun snapshots(request: QuestionSnapshotQuery): List<BankQuestionSnapshot> {
                calls += "fetch"
                return request.ids.map { id ->
                    BankQuestionSnapshot(id, 1, "a".repeat(64), "Math", "Algebra", "easy", "ENEM", 2025, "Q?", listOf("A", "B"), 0, "Exp", null)
                }
            }
        }
        val draft = AssessmentDraft("mod-1", "Quiz", "Desc", AssessmentKind.QUIZ, 10.0, null, true, false)
        val record = AssessmentRecord("assess-1", 1, Instant.EPOCH, Instant.EPOCH, null, draft, AssessmentState.Draft)
        val store = object : AssessmentReadOutputPort, AssessmentWriteOutputPort {
            override fun authorizeWrite(actor: AuthenticatedActor, moduleId: String) { calls += "authorize" }
            override fun replay(request: AssessmentReceiptKey): AssessmentDetails.Staff? = null
            override fun create(request: CreateAssessmentRecord): AssessmentDetails.Staff {
                calls += "create"
                assertEquals("q-1", request.questions.single().id)
                return AssessmentDetails.Staff(record, emptyList(), false)
            }
            override fun update(request: UpdateAssessmentRecord): AssessmentDetails.Staff {
                calls += "update"
                assertEquals("q-2", request.questions?.single()?.id)
                return AssessmentDetails.Staff(record.copy(revision = 2), emptyList(), false)
            }
            override fun publish(request: PublishAssessmentCommand): AssessmentDetails.Staff? = error("Not used")
            override fun assessment(request: GetAssessmentQuery): AssessmentDetails.Staff = AssessmentDetails.Staff(record, emptyList(), false)
            override fun assessments(request: ListAssessmentsQuery): AssessmentResults = error("Not used")
            override fun dashboardAssessments(query: ListDashboardAssessmentsQuery): List<DashboardAssessment> = error("Not used")
            override fun pendingStudentCount(actor: AuthenticatedActor): Int = error("Not used")
        }
        fun bank(id: String) = AssessmentQuestionSelections(listOf(AssessmentQuestionSelections.Selection.Bank(id)))

        val createUseCase = CreateAssessmentUseCase(store, snapshots)
        val updateUseCase = UpdateAssessmentUseCase(store, store, snapshots)
        val created = createUseCase.create(CreateAssessmentCommand(teacher, "idem-create-1234", "a".repeat(64), draft, bank("q-1")))
        val updated = updateUseCase.update(UpdateAssessmentCommand(
            teacher, created.record.id, "idem-update-1234", "b".repeat(64), 1,
            AssessmentChanges(), FieldChange.Set(bank("q-2")), true,
        ))
        assertEquals("assess-1", created.record.id)
        assertEquals(2, updated.record.revision)
        assertEquals(listOf("authorize", "fetch", "create", "authorize", "fetch", "update"), calls)
    }

    @Test
    fun `ManageContent publish executes inside TransactionRunnerOutputPort and rolls back on failure`() {
        var inTx = false
        var rolledBack = false
        var committed = false
        val runner = object : TransactionRunnerOutputPort {
            override fun <T> run(block: () -> T): T {
                inTx = true
                return try {
                    block().also { committed = true }
                } catch (e: Throwable) {
                    rolledBack = true
                    throw e
                } finally {
                    inTx = false
                }
            }
        }
        var authorizeInTx = false
        var publishInTx = false
        val contentStore = object : MaterialWriteOutputPort {
            override fun authorizeMaterialPublication(actor: AuthenticatedActor, moduleId: String) {
                authorizeInTx = inTx
            }
            override fun publishMaterials(request: StorePublishMaterialsCommand): Page<Material> {
                publishInTx = inTx
                return Page(emptyList(), 1, 20, 0)
            }
            override fun updateMaterial(request: StoreUpdateMaterialCommand): Material = error("Unexpected update")
            override fun reorderMaterials(request: ReorderMaterialsCommand): Page<Material> = error("Unexpected reorder")
        }
        val failingIdempotency = object : EncodedIdempotencyStoreOutputPort {
            override fun findEncoded(request: FindIdempotencyDto): String? = null
            override fun saveEncoded(request: FindIdempotencyDto, response: String) {
                throw IllegalStateException("idempotency save failed")
            }
        }

        val contentUseCase = PublishMaterialsUseCase(contentStore, failingIdempotency, JacksonMaterialPublicationReceipt(mapper), runner)
        val failure = assertThrows(IllegalStateException::class.java) {
            contentUseCase.publish(PublishMaterialsCommand(teacher, "idem-pub-1234", "mod-1", 1, listOf(MaterialDraft("mod-1", "L1", MaterialKind.LESSON, 10.0, "Body", null, null))))
        }
        assertEquals("idempotency save failed", failure.message)
        assertTrue(authorizeInTx)
        assertTrue(publishInTx)
        assertTrue(rolledBack)
        assertFalse(committed)
    }

    @Test
    fun `ManageTopics reply executes inside TransactionRunnerOutputPort and rolls back on failure`() {
        var inTx = false
        var rolledBack = false
        var committed = false
        val runner = object : TransactionRunnerOutputPort {
            override fun <T> run(block: () -> T): T {
                inTx = true
                return try {
                    block().also { committed = true }
                } catch (e: Throwable) {
                    rolledBack = true
                    throw e
                } finally {
                    inTx = false
                }
            }
        }
        var authorizeReplyInTx = false
        var idempotencyFindInTx = false
        var replyInTx = false
        var idempotencySaveInTx = false
        val topicStore = object : TopicStoreOutputPort {
            override fun authorizeAndFindReplay(request: FindTopicReplayQuery): ForumTopicView? = null
            override fun authorizeReply(request: AuthorizeTopicReplyCommand) { authorizeReplyInTx = inTx }
            override fun findReplyReceipt(request: FindForumReplyReceiptQuery): ForumTopicView? { idempotencyFindInTx = inTx; return null }
            override fun saveReplyReceipt(request: SaveForumReplyReceiptCommand) { idempotencySaveInTx = inTx; throw IllegalStateException("idempotency save failed") }
            override fun create(request: CreateTopicRecord) = forumTopic("topic-1")
            override fun topics(request: ListStoredTopicsQuery) = ForumTopicPage(emptyList(), 1, 20, 0)
            override fun dashboardTopics(request: ListDashboardStoredTopicsQuery) = emptyList<ForumDashboardTopic>()
            override fun topic(request: GetStoredTopicQuery): ForumTopicView? = null
            override fun reply(request: CreateTopicReplyRecord): ForumTopicView? {
                replyInTx = inTx
                return forumTopic("topic-1")
            }
            override fun read(request: MarkTopicReadRecord): ForumTopicView? = null
            override fun solution(request: MarkTopicSolutionRecord): ForumTopicView? = null
            override fun withdraw(request: WithdrawTopicRecord): ForumTopicView? = null
            override fun vote(request: VoteTopicReplyRecord): ForumTopicView? = null
            override fun withdrawReply(request: WithdrawTopicReplyRecord): ForumTopicView? = null
        }
        val snapshots = object : QuestionSnapshotProviderOutputPort {
            override fun snapshots(request: br.com.ajadigital.core.assessment.application.models.QuestionSnapshotQuery): List<BankQuestionSnapshot> = emptyList()
        }
        val topicsUseCase = ReplyToTopicUseCase(topicStore, fingerprints, runner)
        assertThrows(IllegalStateException::class.java) {
            topicsUseCase.reply(ReplyToTopicCommand(teacher, "topic-1", "Helpful reply", "idem-reply-1234"))
        }
        assertTrue(authorizeReplyInTx)
        assertTrue(idempotencyFindInTx)
        assertTrue(replyInTx)
        assertTrue(idempotencySaveInTx)
        assertTrue(rolledBack)
        assertFalse(committed)
    }
}
