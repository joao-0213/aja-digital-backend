package br.com.ajadigital.core.assessment

import br.com.ajadigital.core.assessment.domain.models.AssessmentKind
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Conflict
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Forbidden
import br.com.ajadigital.core.assessment.application.models.AssessmentChanges
import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.AssessmentQuestionSelections
import br.com.ajadigital.core.assessment.application.models.AssessmentResults
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentCommand
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentWriteOutputPort
import br.com.ajadigital.core.assessment.application.usecases.UpdateAssessmentUseCase
import br.com.ajadigital.core.assessment.application.contracts.DashboardAssessment
import br.com.ajadigital.core.assessment.application.contracts.ListDashboardAssessmentsQuery
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionContent
import br.com.ajadigital.core.assessment.domain.models.AssessmentRecord
import br.com.ajadigital.core.assessment.domain.models.AssessmentState
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.application.models.ListAssessmentsQuery
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentRecord
import br.com.ajadigital.core.assessment.application.models.PublishAssessmentCommand
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentRecord
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.application.models.QuestionSnapshotQuery
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.FieldChange
import br.com.ajadigital.core.assessment.application.models.AssessmentReceiptKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class UpdateAssessmentUseCaseTests {
    private val teacher = AuthenticatedActor("teacher-1", "teacher", true, Instant.EPOCH, "session-hash")
    private val request = UpdateAssessmentCommand(
        teacher, "assessment-1", "idempotency-123", "a".repeat(64), 1,
        AssessmentChanges(), FieldChange.Unchanged, false,
    )
    private val store = RecordingStore()
    private val useCase = UpdateAssessmentUseCase(store, store, object : QuestionSnapshotProviderOutputPort {
        override fun snapshots(request: QuestionSnapshotQuery): List<BankQuestionSnapshot> =
            error("No outside questions were requested")
    })

    @Test
    fun `typed edit changes only explicitly supplied fields`() {
        useCase.update(request.copy(changes = AssessmentChanges(deadline = FieldChange.Set(null)), confirmResetDrafts = true))
        val update = checkNotNull(store.updated)
        assertNull(update.draft?.deadline)
        assertEquals("Existing title", update.draft?.title)
        assertEquals(true, update.confirmResetDrafts)
    }

    @Test
    fun `the exact typed input port rejects a student before a store call`() {
        val student = AuthenticatedActor("student-1", "student", true, Instant.EPOCH, "student-session")
        assertThrows(Forbidden::class.java) { useCase.update(request.copy(actor = student)) }
        assertEquals(0, store.readCount)
        assertNull(store.updated)
    }

    @Test
    fun `kind change without a quiz composition cannot reach the write port`() {
        val edit = request.copy(changes = AssessmentChanges(kind = FieldChange.Set(AssessmentKind.QUIZ)))
        assertThrows(Conflict::class.java) { useCase.update(edit) }
        assertNull(store.updated)
    }

    @Test
    fun `question inputs retain neither the selection nor the option buffer`() {
        val options = mutableListOf("A", "B")
        val content = AssessmentQuestionContent("Math", "Sets", "easy", "AJA", 2026, "Question", options, null, 0, "Explanation")
        val input = mutableListOf<AssessmentQuestionSelections.Selection>(AssessmentQuestionSelections.Selection.Authored("author-1", null, content))
        val questions = AssessmentQuestionSelections(input)
        input.clear()
        options[0] = "Modified"
        assertEquals(1, questions.items.size)
        assertEquals(listOf("A", "B"), content.options)
    }

    private class RecordingStore : AssessmentReadOutputPort, AssessmentWriteOutputPort {
        var updated: UpdateAssessmentRecord? = null
        var readCount = 0

        override fun authorizeWrite(actor: AuthenticatedActor, moduleId: String) = Unit
        override fun replay(request: AssessmentReceiptKey): AssessmentDetails.Staff? = null
        override fun create(request: CreateAssessmentRecord): AssessmentDetails.Staff = error("Not used")
        override fun update(request: UpdateAssessmentRecord): AssessmentDetails.Staff {
            updated = request
            return current(request.id)
        }
        override fun publish(request: PublishAssessmentCommand): AssessmentDetails.Staff? = error("Not used")
        override fun assessment(request: GetAssessmentQuery): AssessmentDetails.Staff {
            readCount++
            return current(request.id)
        }
        override fun assessments(request: ListAssessmentsQuery): AssessmentResults = error("Not used")
        override fun dashboardAssessments(query: ListDashboardAssessmentsQuery): List<DashboardAssessment> = error("Not used")
        override fun pendingStudentCount(actor: AuthenticatedActor): Int = error("Not used")

        private fun current(id: String): AssessmentDetails.Staff {
            val draft = AssessmentDraft("module-1", "Existing title", "", AssessmentKind.WORK, null, Instant.EPOCH, false, false)
            val facts = AssessmentRecord(id, 1, Instant.EPOCH, Instant.EPOCH, null, draft, AssessmentState.Draft)
            return AssessmentDetails.Staff(facts, emptyList(), false)
        }
    }
}
