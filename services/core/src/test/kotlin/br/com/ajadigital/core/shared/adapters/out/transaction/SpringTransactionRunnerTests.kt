package br.com.ajadigital.core.shared.adapters.out.transaction

import br.com.ajadigital.core.forum.domain.models.*
import br.com.ajadigital.core.forum.application.models.*
import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentDto
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentDto
import br.com.ajadigital.core.assessment.domain.models.AssessmentPage
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.assessment.domain.ports.out.*
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.assessment.application.usecases.CreateAssessmentUseCase
import br.com.ajadigital.core.assessment.application.usecases.UpdateAssessmentUseCase
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.models.AcademicPage
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.usecases.CreateTopicUseCase
import br.com.ajadigital.core.forum.application.usecases.ReplyToTopicUseCase
import br.com.ajadigital.core.materials.domain.models.DashboardContent
import br.com.ajadigital.core.materials.application.models.PublishMaterialsDto
import br.com.ajadigital.core.materials.domain.models.DownloadedFile
import br.com.ajadigital.core.materials.domain.models.MaterialCreate
import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort
import br.com.ajadigital.core.materials.application.usecases.PublishMaterialsUseCase
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import br.com.ajadigital.core.shared.adapters.out.serialization.JacksonJsonSerializer
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.toRequestValue
import java.time.Instant

class SpringTransactionRunnerTests {
    private val mapper = JsonMapper.builder().build()
    private val serializer = JacksonJsonSerializer(mapper)
    private val teacher = AuthenticatedActor("teacher-1", "teacher", true, Instant.parse("2026-01-01T00:00:00Z"), "hash-1")

    @Test
    fun `ManageTopics create calls question snapshots outside TransactionRunnerOutputPort`() {
        var inTx = false
        var snapshotInTx: Boolean? = null
        var txEntries = 0
        val runner = object : TransactionRunnerOutputPort {
            override fun <T> run(request: TransactionRunDto<T>): T {
                txEntries++
                inTx = true
                return try { request.block() } finally { inTx = false }
            }
        }
        val snapshots = object : QuestionSnapshotProviderOutputPort {
            override fun snapshots(request: br.com.ajadigital.core.assessment.domain.models.QuestionSnapshotProviderSnapshotsDto): List<BankQuestionSnapshot> { val (ids) = request; snapshotInTx = inTx; return ids.map { id -> BankQuestionSnapshot(id, 1, "hash-$id", "Math", "Algebra", "easy", "ENEM", 2025, "Q?", listOf("A", "B"), 0, "Exp", null) } }
        }
        val store = object : TopicStoreOutputPort {
            override fun authorizeAndFindReplay(request: AuthorizeAndFindTopicReplayDto): Map<String, Any?>? = null
            override fun authorizeReply(request: AuthorizeTopicReplyDto) {}
            override fun create(request: CreateTopicRecordDto) = mapOf("id" to "topic-1")
            override fun topics(request: ListStoredTopicsDto) = AcademicPage(emptyList(), 1, 20, 0)
            override fun dashboardTopics(request: ListDashboardStoredTopicsDto) = emptyList<Map<String, Any?>>()
            override fun topic(request: GetStoredTopicDto): Map<String, Any?>? = null
            override fun reply(request: CreateTopicReplyRecordDto): Map<String, Any?>? = null
            override fun read(request: MarkTopicReadRecordDto): Map<String, Any?>? = null
            override fun solution(request: MarkTopicSolutionRecordDto): Map<String, Any?>? = null
            override fun withdraw(request: WithdrawTopicRecordDto): Map<String, Any?>? = null
            override fun vote(request: VoteTopicReplyRecordDto): Map<String, Any?>? = null
            override fun withdrawReply(request: WithdrawTopicReplyRecordDto): Map<String, Any?>? = null
        }
        val idempotency = object : IdempotencyStoreOutputPort {
            override fun find(request: FindIdempotencyDto): Map<String, Any?>? = null
            override fun save(request: SaveIdempotencyDto) {}
        }

        val useCase = CreateTopicUseCase(store, snapshots, serializer)
        val result = useCase.create(CreateTopicDto(teacher, TopicDraft("class-1", "sub-1", "Title", "Body", emptyList(), "question", "q-1", null), "idem-key-1234"))

        assertEquals("topic-1", result["id"])
        assertEquals(false, snapshotInTx)
        assertEquals(0, txEntries)
    }

    @Test
    fun `ManageAssessments create and update call question snapshots without TransactionRunnerOutputPort`() {
        var snapshotCalls = 0
        val snapshots = object : QuestionSnapshotProviderOutputPort {
            override fun snapshots(request: br.com.ajadigital.core.assessment.domain.models.QuestionSnapshotProviderSnapshotsDto): List<BankQuestionSnapshot> { val (ids) = request; snapshotCalls++; return ids.map { id -> BankQuestionSnapshot(id, 1, "a".repeat(64), "Math", "Algebra", "easy", "ENEM", 2025, "Q?", listOf("A", "B"), 0, "Exp", null) } }
        }
        val store = object : AssessmentStoreOutputPort {
            override fun authorizeWrite(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAuthorizeWriteDto): Unit {}
            override fun create(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStoreCreateDto): Map<String, Any?> = mapOf("id" to "assess-1")
            override fun update(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStoreUpdateDto): Map<String, Any?>? = mapOf("id" to request.id, "revision" to 2)
            override fun publish(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStorePublishDto): Map<String, Any?>? = throw UnsupportedOperationException()
            override fun assessment(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAssessmentDto): Map<String, Any?>? = mapOf("id" to request.id, "revision" to 1, "moduleId" to "mod-1", "title" to "Quiz", "description" to "Desc", "kind" to "quiz", "maxGrade" to 10.0, "deadline" to null, "required" to true, "allowLate" to false, "questionIds" to listOf("q-1"))
            override fun assessments(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStoreAssessmentsDto): AssessmentPage = throw UnsupportedOperationException()
            override fun dashboardAssessments(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStoreDashboardAssessmentsDto): AssessmentPage = throw UnsupportedOperationException()
            override fun pendingStudentCount(request: br.com.ajadigital.core.assessment.domain.models.AssessmentStorePendingStudentCountDto): Int = throw UnsupportedOperationException()
        }
        val idempotency = object : IdempotencyStoreOutputPort {
            override fun find(request: FindIdempotencyDto): Map<String, Any?>? = null
            override fun save(request: SaveIdempotencyDto) {}
        }

        val createUseCase = CreateAssessmentUseCase(store, snapshots, idempotency, serializer)
        val updateUseCase = UpdateAssessmentUseCase(store, snapshots, idempotency, serializer)
        val createBody = mapper.readTree("""{"moduleId":"mod-1","title":"Quiz","description":"Desc","kind":"quiz","maxGrade":10,"deadline":null,"required":true,"allowLate":false,"questions":[{"source":"bank","questionId":"q-1"}]}""")
        val updateBody = mapper.readTree("""{"expectedRevision":1,"confirmResetDrafts":true,"questions":[{"source":"bank","questionId":"q-2"}]}""")

        assertEquals("assess-1", createUseCase.create(CreateAssessmentDto(teacher, "idem-create-1234", createBody.toRequestValue()))["id"])
        assertEquals(2, updateUseCase.update(UpdateAssessmentDto(teacher, "assess-1", "idem-update-1234", updateBody.toRequestValue()))["revision"])
        assertEquals(2, snapshotCalls)
    }

    @Test
    fun `ManageContent publish executes inside TransactionRunnerOutputPort and rolls back on failure`() {
        var inTx = false
        var rolledBack = false
        var committed = false
        val runner = object : TransactionRunnerOutputPort {
            override fun <T> run(request: TransactionRunDto<T>): T {
                inTx = true
                return try {
                    request.block().also { committed = true }
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
        val contentStore = object : ContentStoreOutputPort {
            override fun upload(request: br.com.ajadigital.core.materials.application.models.ContentStoreUploadDto): Map<String, Any?> = throw UnsupportedOperationException()
            override fun provisionQuestionImage(request: br.com.ajadigital.core.materials.domain.models.ContentStoreProvisionQuestionImageDto): Map<String, Any?> = throw UnsupportedOperationException()
            override fun file(request: br.com.ajadigital.core.materials.domain.models.ContentStoreFileDto): Map<String, Any?>? = throw UnsupportedOperationException()
            override fun download(request: br.com.ajadigital.core.materials.domain.models.ContentStoreDownloadDto): DownloadedFile? = throw UnsupportedOperationException()
            override fun discard(request: br.com.ajadigital.core.materials.domain.models.ContentStoreDiscardDto): Unit = throw UnsupportedOperationException()
            override fun materials(request: br.com.ajadigital.core.materials.domain.models.ContentStoreMaterialsDto): AcademicPage = throw UnsupportedOperationException()
            override fun material(request: br.com.ajadigital.core.materials.application.models.ContentStoreMaterialDto): Map<String, Any?>? = throw UnsupportedOperationException()
            override fun authorizeMaterialPublication(request: br.com.ajadigital.core.materials.application.models.ContentStoreAuthorizeMaterialPublicationDto): Unit { authorizeInTx = inTx }
            override fun publishMaterials(request: br.com.ajadigital.core.materials.application.models.ContentStorePublishMaterialsDto): AcademicPage? = run { publishInTx = inTx; AcademicPage(listOf(mapOf("id" to "mat-1")), 1, 20, 1) }
            override fun updateMaterial(request: br.com.ajadigital.core.materials.application.models.ContentStoreUpdateMaterialDto): Map<String, Any?>? = throw UnsupportedOperationException()
            override fun reorderMaterials(request: br.com.ajadigital.core.materials.application.models.ContentStoreReorderMaterialsDto): AcademicPage? = throw UnsupportedOperationException()
            override fun progress(request: br.com.ajadigital.core.materials.application.models.ContentStoreProgressDto): AcademicPage = throw UnsupportedOperationException()
            override fun dashboard(request: br.com.ajadigital.core.materials.application.models.ContentStoreDashboardDto): DashboardContent = throw UnsupportedOperationException()
            override fun recordProgress(request: br.com.ajadigital.core.materials.application.models.ContentStoreRecordProgressDto): Map<String, Any?>? = throw UnsupportedOperationException()
        }
        val failingIdempotency = object : IdempotencyStoreOutputPort {
            override fun find(request: FindIdempotencyDto): Map<String, Any?>? = null
            override fun save(request: SaveIdempotencyDto) {
                throw IllegalStateException("idempotency save failed")
            }
        }

        val contentUseCase = PublishMaterialsUseCase(contentStore, failingIdempotency, serializer, runner)
        assertThrows(IllegalStateException::class.java) {
            contentUseCase.publish(PublishMaterialsDto(teacher, "idem-pub-1234", "mod-1", 1, listOf(MaterialCreate("mod-1", "L1", "lesson", 10.0, "Body", null, null))))
        }
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
            override fun <T> run(request: TransactionRunDto<T>): T {
                inTx = true
                return try {
                    request.block().also { committed = true }
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
            override fun authorizeAndFindReplay(request: AuthorizeAndFindTopicReplayDto): Map<String, Any?>? = null
            override fun authorizeReply(request: AuthorizeTopicReplyDto) { authorizeReplyInTx = inTx }
            override fun create(request: CreateTopicRecordDto) = emptyMap<String, Any?>()
            override fun topics(request: ListStoredTopicsDto) = AcademicPage(emptyList(), 1, 20, 0)
            override fun dashboardTopics(request: ListDashboardStoredTopicsDto) = emptyList<Map<String, Any?>>()
            override fun topic(request: GetStoredTopicDto): Map<String, Any?>? = null
            override fun reply(request: CreateTopicReplyRecordDto): Map<String, Any?>? {
                replyInTx = inTx
                return mapOf("id" to "reply-1")
            }
            override fun read(request: MarkTopicReadRecordDto): Map<String, Any?>? = null
            override fun solution(request: MarkTopicSolutionRecordDto): Map<String, Any?>? = null
            override fun withdraw(request: WithdrawTopicRecordDto): Map<String, Any?>? = null
            override fun vote(request: VoteTopicReplyRecordDto): Map<String, Any?>? = null
            override fun withdrawReply(request: WithdrawTopicReplyRecordDto): Map<String, Any?>? = null
        }
        val snapshots = object : QuestionSnapshotProviderOutputPort {
            override fun snapshots(request: br.com.ajadigital.core.assessment.domain.models.QuestionSnapshotProviderSnapshotsDto): List<BankQuestionSnapshot> = emptyList()
        }
        val failingIdempotency = object : IdempotencyStoreOutputPort {
            override fun find(request: FindIdempotencyDto): Map<String, Any?>? {
                idempotencyFindInTx = inTx
                return null
            }
            override fun save(request: SaveIdempotencyDto) {
                idempotencySaveInTx = inTx
                throw IllegalStateException("idempotency save failed")
            }
        }

        val topicsUseCase = ReplyToTopicUseCase(topicStore, failingIdempotency, serializer, runner)
        assertThrows(IllegalStateException::class.java) {
            topicsUseCase.reply(ReplyToTopicDto(teacher, "topic-1", "Helpful reply", "idem-reply-1234"))
        }
        assertTrue(authorizeReplyInTx)
        assertTrue(idempotencyFindInTx)
        assertTrue(replyInTx)
        assertTrue(idempotencySaveInTx)
        assertTrue(rolledBack)
        assertFalse(committed)
    }
}
