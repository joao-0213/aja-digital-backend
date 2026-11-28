package br.com.ajadigital.core.curriculum.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumConflict
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumForbidden
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumIdempotencyConflict
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumNotFound
import br.com.ajadigital.core.curriculum.domain.exceptions.InvalidAcademicReference
import br.com.ajadigital.core.curriculum.domain.exceptions.UnknownPublication
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class CurriculumExceptionHandler {
    @ExceptionHandler(CurriculumForbidden::class)
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "This action is not allowed"))

    @ExceptionHandler(CurriculumNotFound::class)
    fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))

    @ExceptionHandler(CurriculumConflict::class)
    fun conflict(exception: CurriculumConflict): ResponseEntity<Any> {
        val body = mutableMapOf<String, Any?>(
            "code" to "REVISION_CONFLICT",
            "message" to "Resource changed or conflicts with current state",
            "requestId" to UUID.randomUUID().toString(),
        )
        exception.currentRevision?.let { body["details"] = mapOf("currentRevision" to it) }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to body))
    }

    @ExceptionHandler(CurriculumIdempotencyConflict::class)
    fun idempotencyConflict() = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorBody("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request"))

    @ExceptionHandler(UnknownPublication::class)
    fun invalidPublication() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Publication is not visible in this subject"))

    @ExceptionHandler(InvalidAcademicReference::class)
    fun invalidReference() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "A referenced user is not eligible"))
}
