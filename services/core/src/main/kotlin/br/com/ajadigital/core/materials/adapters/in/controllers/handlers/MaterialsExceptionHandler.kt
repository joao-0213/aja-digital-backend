package br.com.ajadigital.core.materials.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.materials.domain.exceptions.InvalidMaterialEdit
import br.com.ajadigital.core.materials.domain.exceptions.InvalidQuestionServiceCredential
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsConflict
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsForbidden
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsIdempotencyConflict
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class MaterialsExceptionHandler {
    @ExceptionHandler(MaterialsForbidden::class)
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "This action is not allowed"))

    @ExceptionHandler(InvalidQuestionServiceCredential::class)
    fun invalidService() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "Service credential is invalid"))

    @ExceptionHandler(MaterialsNotFound::class)
    fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))

    @ExceptionHandler(InvalidMaterialEdit::class)
    fun invalidEdit() = ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
        .body(errorBody("VALIDATION_ERROR", "Request is invalid"))

    @ExceptionHandler(MaterialsConflict::class)
    fun conflict() = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorBody("REVISION_CONFLICT", "Resource changed or conflicts with current state"))

    @ExceptionHandler(MaterialsIdempotencyConflict::class)
    fun idempotencyConflict() = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorBody("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request"))
}
