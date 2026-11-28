package br.com.ajadigital.core.announcements.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementConflict
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementForbidden
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementIdempotencyConflict
import br.com.ajadigital.core.announcements.domain.exceptions.AnnouncementNotFound
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.FeatureHttpErrors
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AnnouncementsExceptionHandler {
    @ExceptionHandler(AnnouncementForbidden::class)
    fun forbidden() = FeatureHttpErrors.forbidden()

    @ExceptionHandler(AnnouncementNotFound::class)
    fun notFound() = FeatureHttpErrors.notFound()

    @ExceptionHandler(AnnouncementConflict::class)
    fun conflict(exception: AnnouncementConflict) = FeatureHttpErrors.conflict(exception.currentRevision)

    @ExceptionHandler(AnnouncementIdempotencyConflict::class)
    fun idempotencyConflict() = FeatureHttpErrors.idempotencyConflict()
}
