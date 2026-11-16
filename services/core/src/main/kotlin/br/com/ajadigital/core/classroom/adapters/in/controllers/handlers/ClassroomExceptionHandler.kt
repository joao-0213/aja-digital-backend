package br.com.ajadigital.core.classroom.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID


@RestControllerAdvice
class ClassroomExceptionHandler {
    @ExceptionHandler(AcademicForbidden::class)
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "This action is not allowed"))
    @ExceptionHandler(AcademicNotFound::class)
    fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))
    @ExceptionHandler(AcademicConflict::class)
    fun conflict(exception: AcademicConflict): ResponseEntity<Any> {
        val body = mutableMapOf<String, Any?>("code" to "REVISION_CONFLICT", "message" to "Resource changed or conflicts with current state", "requestId" to UUID.randomUUID().toString())
        exception.currentRevision?.let { body["details"] = mapOf("currentRevision" to it) }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to body))
    }
    @ExceptionHandler(AcademicIdempotencyConflict::class)
    fun idempotencyConflict() = ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request"))
}
