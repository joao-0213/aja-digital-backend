package br.com.ajadigital.core.assessment.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure
import br.com.ajadigital.core.assessment.domain.exceptions.InvalidAttemptAnswers
import br.com.ajadigital.core.assessment.domain.exceptions.InvalidSubmissionFeedback
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionSnapshotNotFound
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID


@RestControllerAdvice
class AssessmentExceptionHandler {
    @ExceptionHandler(AssessmentAuthoringFailure::class)
    fun authoringFailure(failure: AssessmentAuthoringFailure): ResponseEntity<*> = when (failure) {
        is AssessmentAuthoringFailure.Forbidden ->
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "This action is not allowed"))
        is AssessmentAuthoringFailure.NotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))
        is AssessmentAuthoringFailure.Conflict -> {
            val error = linkedMapOf<String, Any?>(
                "code" to "REVISION_CONFLICT", "message" to "Resource changed or conflicts with current state",
                "requestId" to UUID.randomUUID().toString(),
            )
            failure.currentRevision?.let { error["details"] = mapOf("currentRevision" to it) }
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to error))
        }
        is AssessmentAuthoringFailure.IdempotencyConflict -> ResponseEntity.status(HttpStatus.CONFLICT)
            .body(errorBody("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request"))
    }

    @ExceptionHandler(InvalidSubmissionFeedback::class)
    fun invalidSubmissionFeedback() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Feedback is invalid"))
    @ExceptionHandler(QuestionSnapshotNotFound::class)
    fun missingSnapshot() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))
    @ExceptionHandler(QuestionsUnavailable::class)
    fun unavailableSnapshots() = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody("SERVICE_UNAVAILABLE", "Question snapshots are unavailable"))
    @ExceptionHandler(InvalidAttemptAnswers::class)
    fun invalidAttemptAnswers() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "An answer is not valid for this attempt"))
}
