package br.com.ajadigital.core.assessment

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.assessment.application.models.*
import br.com.ajadigital.core.assessment.application.models.SubmissionContracts
import br.com.ajadigital.core.assessment.application.models.SubmitSubmissionCommand
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.application.usecases.CreateSubmissionUseCase
import br.com.ajadigital.core.assessment.application.usecases.SubmitSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.models.*
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class SubmissionUseCaseTests {
    @Test
    fun `creation rejects staff before store call`() {
        val store = RecordingStore()
        assertThrows(AcademicForbidden::class.java) {
            CreateSubmissionUseCase(store).create(CreateSubmissionCommand(actor("teacher"), "submission-key", SubmissionContracts.CreateBody(null, null, false), "hash"))
        }
        assertFalse(store.called)
    }

    @Test
    fun `creation rejects duplicate file ids and credential-bearing links before store call`() {
        val duplicateFiles = SubmissionContracts.CreateBody("assessment-1", listOf(
            SubmissionContracts.AttachmentBody("file", "work.pdf", fileId = "file-1", fieldsValid = true),
            SubmissionContracts.AttachmentBody("file", "copy.pdf", fileId = "file-1", fieldsValid = true),
        ), true)
        val store = RecordingStore()
        assertThrows(IllegalArgumentException::class.java) {
            CreateSubmissionUseCase(store).create(CreateSubmissionCommand(actor("student"), "submission-key", duplicateFiles, "hash"))
        }
        val credentialLink = SubmissionContracts.CreateBody("assessment-1", listOf(
            SubmissionContracts.AttachmentBody("link", "private", url = "https://user:secret@example.test/work", fieldsValid = true),
        ), true)
        assertThrows(IllegalArgumentException::class.java) {
            CreateSubmissionUseCase(store).create(CreateSubmissionCommand(actor("student"), "submission-key", credentialLink, "hash"))
        }
        assertFalse(store.called)
    }

    @Test
    fun `submission rejects invalid revision and forwards the original hash for a valid command`() {
        val store = RecordingStore()
        val useCase = SubmitSubmissionUseCase(store)
        assertThrows(IllegalArgumentException::class.java) {
            useCase.submit(SubmitSubmissionCommand(actor("student"), "submission-1", "submit-key", SubmissionContracts.SubmitBody(0, true), "hash"))
        }
        assertFalse(store.called)

        val result = useCase.submit(SubmitSubmissionCommand(actor("student"), "submission-1", "submit-key", SubmissionContracts.SubmitBody(2, true), "original-hash"))
        assertEquals(store.submitted, result)
        assertEquals("original-hash", store.request?.requestHash)
        assertEquals(2, store.request?.expectedRevision)
    }

    @Test
    fun `submission projections preserve immutable collections and published feedback beside draft`() {
        val inputAttachments = mutableListOf(SubmissionContracts.AttachmentBody("link", "Essay", url = "https://example.test/essay", fieldsValid = true))
        val createBody = SubmissionContracts.CreateBody("assessment-1", inputAttachments, true)
        val updateBody = SubmissionContracts.UpdateBody(1, true, inputAttachments, true)
        inputAttachments.clear()
        assertEquals(1, createBody.attachments?.size)
        assertEquals(1, updateBody.attachments?.size)
        val attachment = SubmissionView.Attachment("file", "essay.pdf", fileId = "file-1")
        val source = mutableListOf(attachment)
        val published = SubmissionView.Feedback("previous", SubmissionView.Grade.Decimal(BigDecimal("7.5")), SubmissionView.Author("teacher-1", "Teacher"), "2026-01-01T00:00:00Z")
        val draft = SubmissionView.Feedback("new draft", null, SubmissionView.Author("teacher-2", "Staff"), "2026-01-02T00:00:00Z")
        val view = SubmissionView.StaffSubmitted(
            "submission-1", 4, "created", "updated", "teacher-2", "assessment-1", "student-1", source,
            "2026-01-01T00:00:00Z", SubmissionView.Receipt("receipt-1", "submission-1", "2026-01-01T00:00:00Z"),
            published, true, "Student", draft, listOf(published),
        )
        source.clear()
        assertEquals(listOf(attachment), view.attachments)
        assertEquals("previous", view.feedbackPublished?.comment)
        assertTrue(view.feedbackPublished?.grade is SubmissionView.Grade.Decimal)
        assertEquals("new draft", view.feedbackDraft?.comment)
        assertEquals(listOf(published), view.feedbackHistory)
        assertTrue(runCatching { (view.feedbackHistory as MutableList).clear() }.isFailure)

        val pageSource = mutableListOf<SubmissionView>(view)
        val page = SubmissionPage(pageSource, 1, 1, 1L)
        pageSource.clear()
        assertEquals(listOf(view), page.items)
        assertTrue(runCatching { (page.items as MutableList).clear() }.isFailure)
        assertEquals(0L, SubmissionPage(listOf(view), 1, 1, 0L).total)
        assertThrows(IllegalArgumentException::class.java) { SubmissionPage(emptyList(), 0, 1, 0L) }
        assertThrows(IllegalArgumentException::class.java) { SubmissionPage(emptyList(), 1, 101, 0L) }
        assertThrows(IllegalArgumentException::class.java) { SubmissionPage(emptyList(), 1, 1, -1L) }
    }

    private fun actor(role: String) = AuthenticatedActor("user-1", role, true, Instant.EPOCH, "opaque")

    private class RecordingStore : SubmissionStoreOutputPort {
        var called = false
        var request: SubmissionSubmitRecord? = null
        val submitted = SubmissionView.StudentSubmitted(
            "submission-1", 3, "created", "updated", "student-1", "assessment-1", "student-1", emptyList(),
            "2026-01-01T00:00:00Z", SubmissionView.Receipt("receipt-1", "submission-1", "2026-01-01T00:00:00Z"), null, false,
        )
        override fun create(request: SubmissionCreateRecord): SubmissionView = fail()
        override fun submissions(request: SubmissionsListQuery): SubmissionPage = fail()
        override fun pendingFeedbackCount(request: PendingFeedbackCountQuery): Int = fail()
        override fun submission(request: SubmissionGetQuery): SubmissionView? = fail()
        override fun update(request: SubmissionUpdateRecord): SubmissionView? = fail()
        override fun submit(request: SubmissionSubmitRecord): SubmissionView { called = true; this.request = request; return submitted }
        override fun saveFeedback(request: SubmissionSaveFeedbackRecord): SubmissionView? = fail()
        override fun publishFeedback(request: SubmissionPublishFeedbackRecord): SubmissionView? = fail()
        private fun <T> fail(): T = error("unexpected store call")
    }
}
