package br.com.ajadigital.core.shared.adapters.out.transaction

import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentPage
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.assessment.domain.usecases.ManageAssessments
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.forum.domain.usecases.ManageTopics
import br.com.ajadigital.core.materials.domain.models.DashboardContent
import br.com.ajadigital.core.materials.domain.models.DownloadedFile
import br.com.ajadigital.core.materials.domain.models.MaterialCreate
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore
import br.com.ajadigital.core.materials.domain.usecases.ManageContent
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class SpringTransactionRunnerTests {
    private val mapper = JsonMapper.builder().build()
    private val teacher = AcademicActor("teacher-1", "teacher", true, Instant.parse("2026-01-01T00:00:00Z"), "hash-1")

    @Test
    fun `ManageTopics create calls question snapshots outside TransactionRunner`() {
        var inTx = false
        var snapshotInTx: Boolean? = null
        var txEntries = 0
        val runner = object : TransactionRunner {
            override fun <T> run(block: () -> T): T {
                txEntries++
                inTx = true
                return try { block() } finally { inTx = false }
            }
        }
        val snapshots = object : QuestionSnapshotProvider {
            override fun snapshots(ids: List<String>): List<BankQuestionSnapshot> {
                snapshotInTx = inTx
                return ids.map { id ->
                    BankQuestionSnapshot(id, 1, "hash-$id", "Math", "Algebra", "easy", "ENEM", 2025, "Q?", listOf("A", "B"), 0, "Exp", null)
                }
            }
        }
        val store = object : TopicStore {
            override fun authorizeAndFindReplay(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String): Map<String, Any?>? = null
            override fun authorizeReply(actorId: String, role: String, sessionHash: String, id: String) {}
            override fun create(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String) = mapOf("id" to "topic-1")
            override fun topics(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?, unread: Boolean?, solved: Boolean?) = AcademicPage(emptyList(), 1, 20, 0)
            override fun dashboardTopics(actorId: String, role: String, sessionHash: String, size: Int) = emptyList<Map<String, Any?>>()
            override fun topic(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? = null
            override fun reply(actorId: String, role: String, sessionHash: String, id: String, body: String): Map<String, Any?>? = null
            override fun read(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? = null
            override fun solution(actorId: String, role: String, sessionHash: String, id: String, revision: Int, replyId: String?): Map<String, Any?>? = null
            override fun withdraw(actorId: String, role: String, sessionHash: String, id: String, revision: Int): Map<String, Any?>? = null
            override fun vote(actorId: String, role: String, sessionHash: String, id: String, replyId: String, voted: Boolean): Map<String, Any?>? = null
            override fun withdrawReply(actorId: String, role: String, sessionHash: String, id: String, replyId: String, revision: Int): Map<String, Any?>? = null
        }
        val idempotency = object : IdempotencyStore {
            override fun find(actorId: String, operation: String, target: String, key: String, requestHash: String): Map<String, Any?>? = null
            override fun save(actorId: String, operation: String, target: String, key: String, requestHash: String, response: Map<String, Any?>) {}
        }

        val useCase = ManageTopics(store, snapshots, idempotency, mapper, runner)
        val result = useCase.create(teacher, TopicDraft("class-1", "sub-1", "Title", "Body", emptyList(), "question", "q-1", null), "idem-key-1234")

        assertEquals("topic-1", result["id"])
        assertEquals(false, snapshotInTx)
        assertEquals(0, txEntries)
    }

    @Test
    fun `ManageAssessments create and update call question snapshots without TransactionRunner`() {
        var snapshotCalls = 0
        val snapshots = object : QuestionSnapshotProvider {
            override fun snapshots(ids: List<String>): List<BankQuestionSnapshot> {
                snapshotCalls++
                return ids.map { id ->
                    BankQuestionSnapshot(id, 1, "a".repeat(64), "Math", "Algebra", "easy", "ENEM", 2025, "Q?", listOf("A", "B"), 0, "Exp", null)
                }
            }
        }
        val store = object : AssessmentStore {
            override fun authorizeWrite(actorId: String, role: String, sessionHash: String, moduleId: String) {}
            override fun create(actorId: String, role: String, sessionHash: String, key: String, requestHash: String, draft: AssessmentDraft, questions: List<AssessmentQuestion>) = mapOf("id" to "assess-1")
            override fun update(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int, draft: AssessmentDraft?, questions: List<AssessmentQuestion>?, confirmResetDrafts: Boolean) = mapOf("id" to id, "revision" to 2)
            override fun publish(actorId: String, role: String, sessionHash: String, id: String, key: String, requestHash: String, expectedRevision: Int): Map<String, Any?>? = null
            override fun assessment(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? = mapOf(
                "id" to id, "revision" to 1, "moduleId" to "mod-1", "title" to "Quiz", "description" to "Desc",
                "kind" to "quiz", "maxGrade" to 10.0, "deadline" to null, "required" to true, "allowLate" to false,
                "questionIds" to listOf("q-1"),
            )
            override fun assessments(actorId: String, role: String, sessionHash: String, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?) = AssessmentPage(emptyList(), 1, 20, 0)
            override fun dashboardAssessments(actorId: String, role: String, sessionHash: String, pageSize: Int) = AssessmentPage(emptyList(), 1, 20, 0)
            override fun pendingStudentCount(actorId: String, sessionHash: String) = 0
        }
        val idempotency = object : IdempotencyStore {
            override fun find(actorId: String, operation: String, target: String, key: String, requestHash: String): Map<String, Any?>? = null
            override fun save(actorId: String, operation: String, target: String, key: String, requestHash: String, response: Map<String, Any?>) {}
        }

        val useCase = ManageAssessments(store, snapshots, idempotency, mapper)
        val createBody = mapper.readTree("""{"moduleId":"mod-1","title":"Quiz","description":"Desc","kind":"quiz","maxGrade":10,"deadline":null,"required":true,"allowLate":false,"questions":[{"source":"bank","questionId":"q-1"}]}""")
        val updateBody = mapper.readTree("""{"expectedRevision":1,"confirmResetDrafts":true,"questions":[{"source":"bank","questionId":"q-2"}]}""")

        assertEquals("assess-1", useCase.create(teacher, "idem-create-1234", createBody)["id"])
        assertEquals(2, useCase.update(teacher, "assess-1", "idem-update-1234", updateBody)["revision"])
        assertEquals(2, snapshotCalls)
    }

    @Test
    fun `ManageContent publish executes inside TransactionRunner and rolls back on failure`() {
        var inTx = false
        var rolledBack = false
        var committed = false
        val runner = object : TransactionRunner {
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
        val contentStore = object : ContentStore {
            override fun upload(actorId: String, role: String, sessionHash: String, key: String, name: String, declaredMime: String, purpose: String, parentId: String, bytes: ByteArray) = emptyMap<String, Any?>()
            override fun provisionQuestionImage(id: String, questionId: String, name: String, mime: String, bytes: ByteArray) = emptyMap<String, Any?>()
            override fun file(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>? = null
            override fun download(id: String, actorId: String, role: String, sessionHash: String, disposition: String?): DownloadedFile? = null
            override fun discard(id: String, actorId: String, role: String, sessionHash: String) {}
            override fun materials(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, moduleId: String?, subjectId: String?) = AcademicPage(emptyList(), 1, 20, 0)
            override fun material(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>? = null
            override fun authorizeMaterialPublication(actorId: String, role: String, sessionHash: String, moduleId: String) { authorizeInTx = inTx }
            override fun publishMaterials(actorId: String, role: String, sessionHash: String, moduleId: String, expectedRevision: Int, items: List<MaterialCreate>): AcademicPage? {
                publishInTx = inTx
                return AcademicPage(listOf(mapOf("id" to "mat-1")), 1, 20, 1)
            }
            override fun updateMaterial(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, title: String?, duration: Double?, content: String?, videoUrl: String?): Map<String, Any?>? = null
            override fun reorderMaterials(actorId: String, role: String, sessionHash: String, moduleId: String, expectedRevision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): AcademicPage? = null
            override fun progress(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, subjectId: String?, materialId: String?) = AcademicPage(emptyList(), 1, 20, 0)
            override fun dashboard(actorId: String, role: String, sessionHash: String, progressLimit: Int) = DashboardContent(emptyList(), 0, 0, null)
            override fun recordProgress(actorId: String, role: String, sessionHash: String, materialId: String, completed: Boolean): Map<String, Any?>? = null
        }
        val failingIdempotency = object : IdempotencyStore {
            override fun find(actorId: String, operation: String, target: String, key: String, requestHash: String): Map<String, Any?>? = null
            override fun save(actorId: String, operation: String, target: String, key: String, requestHash: String, response: Map<String, Any?>) {
                throw IllegalStateException("idempotency save failed")
            }
        }

        val contentUseCase = ManageContent(contentStore, failingIdempotency, mapper, runner)
        assertThrows(IllegalStateException::class.java) {
            contentUseCase.publish(teacher, "idem-pub-1234", "mod-1", 1, listOf(MaterialCreate("mod-1", "L1", "lesson", 10.0, "Body", null, null)))
        }
        assertTrue(authorizeInTx)
        assertTrue(publishInTx)
        assertTrue(rolledBack)
        assertFalse(committed)
    }

    @Test
    fun `ManageTopics reply executes inside TransactionRunner and rolls back on failure`() {
        var inTx = false
        var rolledBack = false
        var committed = false
        val runner = object : TransactionRunner {
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
        val topicStore = object : TopicStore {
            override fun authorizeAndFindReplay(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String): Map<String, Any?>? = null
            override fun authorizeReply(actorId: String, role: String, sessionHash: String, id: String) { authorizeReplyInTx = inTx }
            override fun create(actorId: String, role: String, sessionHash: String, draft: TopicDraft, idempotencyKey: String, requestHash: String) = emptyMap<String, Any?>()
            override fun topics(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?, unread: Boolean?, solved: Boolean?) = AcademicPage(emptyList(), 1, 20, 0)
            override fun dashboardTopics(actorId: String, role: String, sessionHash: String, size: Int) = emptyList<Map<String, Any?>>()
            override fun topic(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? = null
            override fun reply(actorId: String, role: String, sessionHash: String, id: String, body: String): Map<String, Any?>? {
                replyInTx = inTx
                return mapOf("id" to "reply-1")
            }
            override fun read(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>? = null
            override fun solution(actorId: String, role: String, sessionHash: String, id: String, revision: Int, replyId: String?): Map<String, Any?>? = null
            override fun withdraw(actorId: String, role: String, sessionHash: String, id: String, revision: Int): Map<String, Any?>? = null
            override fun vote(actorId: String, role: String, sessionHash: String, id: String, replyId: String, voted: Boolean): Map<String, Any?>? = null
            override fun withdrawReply(actorId: String, role: String, sessionHash: String, id: String, replyId: String, revision: Int): Map<String, Any?>? = null
        }
        val snapshots = object : QuestionSnapshotProvider {
            override fun snapshots(ids: List<String>) = emptyList<BankQuestionSnapshot>()
        }
        val failingIdempotency = object : IdempotencyStore {
            override fun find(actorId: String, operation: String, target: String, key: String, requestHash: String): Map<String, Any?>? {
                idempotencyFindInTx = inTx
                return null
            }
            override fun save(actorId: String, operation: String, target: String, key: String, requestHash: String, response: Map<String, Any?>) {
                idempotencySaveInTx = inTx
                throw IllegalStateException("idempotency save failed")
            }
        }

        val topicsUseCase = ManageTopics(topicStore, snapshots, failingIdempotency, mapper, runner)
        assertThrows(IllegalStateException::class.java) {
            topicsUseCase.reply(teacher, "topic-1", "Helpful reply", "idem-reply-1234")
        }
        assertTrue(authorizeReplyInTx)
        assertTrue(idempotencyFindInTx)
        assertTrue(replyInTx)
        assertTrue(idempotencySaveInTx)
        assertTrue(rolledBack)
        assertFalse(committed)
    }
}
