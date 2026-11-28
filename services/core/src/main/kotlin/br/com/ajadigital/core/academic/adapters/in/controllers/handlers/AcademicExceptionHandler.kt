package br.com.ajadigital.core.academic.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.academic.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.FeatureHttpErrors
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
class AcademicExceptionHandler {
    @ExceptionHandler(AcademicForbidden::class)
    fun forbidden() = FeatureHttpErrors.forbidden()
    @ExceptionHandler(AcademicNotFound::class)
    fun notFound() = FeatureHttpErrors.notFound()
    @ExceptionHandler(AcademicConflict::class)
    fun conflict(exception: AcademicConflict) = FeatureHttpErrors.conflict(exception.currentRevision)
    @ExceptionHandler(AcademicIdempotencyConflict::class)
    fun idempotencyConflict() = FeatureHttpErrors.idempotencyConflict()
}
