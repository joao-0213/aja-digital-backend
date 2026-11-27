package br.com.ajadigital.core.assessment

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.assessment.application.models.AttemptAnswerSaveCommand
import br.com.ajadigital.core.assessment.application.models.AttemptGetQuery
import br.com.ajadigital.core.assessment.application.models.AttemptListQuery
import br.com.ajadigital.core.assessment.application.models.AttemptResultQuery
import br.com.ajadigital.core.assessment.application.models.AttemptStartCommand
import br.com.ajadigital.core.assessment.application.models.AttemptSubmitCommand
import br.com.ajadigital.core.assessment.application.models.PracticeBlockedQuery
import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersBody
import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersCommand
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.application.usecases.SaveAttemptAnswersUseCase
import br.com.ajadigital.core.assessment.domain.models.AttemptPage
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.assessment.domain.models.AttemptResult
import br.com.ajadigital.core.assessment.domain.models.AttemptState
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant

class SaveAttemptAnswersUseCaseTests {
    @Test
    fun `forbidden actor is rejected before invalid path or body and store call`() {
        val store = RecordingStore()
        val request = SaveAttemptAnswersCommand(actor("teacher"), "bad id", null)

        assertThrows(AcademicForbidden::class.java) { SaveAttemptAnswersUseCase(store).saveAnswers(request) }
        assertFalse(store.called)
    }

    @Test
    fun `invalid revision is rejected before store call`() {
        val store = RecordingStore()
        val body = SaveAttemptAnswersBody(0, emptyMap(), fieldsValid = true, answersValid = true)

        assertThrows(IllegalArgumentException::class.java) {
            SaveAttemptAnswersUseCase(store).saveAnswers(SaveAttemptAnswersCommand(actor("student"), "attempt-1", body))
        }
        assertFalse(store.called)
    }

    private fun actor(role: String) = AuthenticatedActor("user-1", role, true, Instant.EPOCH, "opaque")

    private class RecordingStore : AttemptStoreOutputPort {
        var called = false
        override fun practiceBlocked(request: PracticeBlockedQuery): Boolean = fail()
        override fun start(request: AttemptStartCommand): AttemptRecord = fail()
        override fun attempts(request: AttemptListQuery): AttemptPage = fail()
        override fun attempt(request: AttemptGetQuery): AttemptRecord? = fail()
        override fun saveAnswers(request: AttemptAnswerSaveCommand): AttemptRecord? { called = true; return null }
        override fun submit(request: AttemptSubmitCommand): AttemptResult? = fail()
        override fun result(request: AttemptResultQuery): AttemptResult? = fail()
        private fun <T> fail(): T = error("unexpected store call")
    }
}
