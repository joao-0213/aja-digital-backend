package br.com.ajadigital.core.assessment.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.assessment.domain.exceptions.InvalidAttemptAnswers
import br.com.ajadigital.core.assessment.domain.exceptions.InvalidSubmissionFeedback
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionSnapshotNotFound
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
class AssessmentExceptionHandler {
    @ExceptionHandler(InvalidSubmissionFeedback::class)
    fun invalidSubmissionFeedback() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Feedback is invalid"))
    @ExceptionHandler(QuestionSnapshotNotFound::class)
    fun missingSnapshot() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))
    @ExceptionHandler(QuestionsUnavailable::class)
    fun unavailableSnapshots() = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorBody("SERVICE_UNAVAILABLE", "Question snapshots are unavailable"))
    @ExceptionHandler(InvalidAttemptAnswers::class)
    fun invalidAttemptAnswers() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "An answer is not valid for this attempt"))
}
