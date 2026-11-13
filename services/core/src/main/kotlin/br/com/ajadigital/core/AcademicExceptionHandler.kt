package br.com.ajadigital.core

import br.com.ajadigital.core.application.academic.AcademicConflict
import br.com.ajadigital.core.application.academic.QuestionSnapshotNotFound
import br.com.ajadigital.core.application.academic.QuestionsUnavailable
import br.com.ajadigital.core.application.academic.AcademicIdempotencyConflict
import br.com.ajadigital.core.application.academic.AcademicForbidden
import br.com.ajadigital.core.application.academic.AcademicNotFound
import br.com.ajadigital.core.application.academic.InvalidAttemptAnswers
import br.com.ajadigital.core.application.academic.InvalidSubmissionFeedback
import br.com.ajadigital.core.application.academic.InvalidAttendanceRecords
import br.com.ajadigital.core.application.academic.InvalidTopicReference
import br.com.ajadigital.core.application.academic.UnknownPublication
import br.com.ajadigital.core.application.academic.InvalidAcademicReference
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.dao.DataIntegrityViolationException
import java.util.UUID


@RestControllerAdvice
class AcademicExceptionHandler {
    @ExceptionHandler(InvalidAttendanceRecords::class)
    fun invalidAttendanceRecords() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Attendance includes a student outside this class session"))
    @ExceptionHandler(InvalidTopicReference::class)
    fun invalidTopicReference() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Referenced content is not visible in this subject"))
    @ExceptionHandler(InvalidSubmissionFeedback::class)
    fun invalidSubmissionFeedback() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Feedback is invalid"))
    @ExceptionHandler(QuestionSnapshotNotFound::class)
    fun missingSnapshot() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Resource was not found"))
    @ExceptionHandler(QuestionsUnavailable::class)
    fun unavailableSnapshots() = ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error("SERVICE_UNAVAILABLE", "Question snapshots are unavailable"))
    @ExceptionHandler(AcademicForbidden::class)
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "This action is not allowed"))
    @ExceptionHandler(UnknownPublication::class)
    fun invalidPublication() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Publication is not visible in this subject"))
    @ExceptionHandler(InvalidAcademicReference::class)
    fun invalidReference() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "A referenced user is not eligible"))
    @ExceptionHandler(InvalidAttemptAnswers::class)
    fun invalidAttemptAnswers() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "An answer is not valid for this attempt"))
    @ExceptionHandler(AcademicNotFound::class)
    fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Resource was not found"))
    @ExceptionHandler(AcademicConflict::class)
    fun conflict(exception: AcademicConflict): ResponseEntity<Any> {
        val body = mutableMapOf<String, Any?>("code" to "REVISION_CONFLICT", "message" to "Resource changed or conflicts with current state", "requestId" to UUID.randomUUID().toString())
        exception.currentRevision?.let { body["details"] = mapOf("currentRevision" to it) }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to body))
    }
    @ExceptionHandler(AcademicIdempotencyConflict::class)
    fun idempotencyConflict() = ResponseEntity.status(HttpStatus.CONFLICT).body(error("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request"))
    @ExceptionHandler(DataIntegrityViolationException::class, IllegalStateException::class)
    fun stateConflict() = ResponseEntity.status(HttpStatus.CONFLICT).body(error("BUSINESS_CONFLICT", "Resource conflicts with current state"))
    @ExceptionHandler(NoSuchElementException::class)
    fun missing() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Resource was not found"))
    private fun error(code: String, message: String) = mapOf("error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString()))
}
