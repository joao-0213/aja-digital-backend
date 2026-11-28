package br.com.ajadigital.core.attendance.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceConflict
import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceForbidden
import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceIdempotencyConflict
import br.com.ajadigital.core.attendance.domain.exceptions.AttendanceNotFound
import br.com.ajadigital.core.attendance.domain.exceptions.InvalidAttendanceRecords
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.FeatureHttpErrors
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AttendanceExceptionHandler {
    @ExceptionHandler(AttendanceForbidden::class)
    fun forbidden() = FeatureHttpErrors.forbidden()

    @ExceptionHandler(AttendanceNotFound::class)
    fun notFound() = FeatureHttpErrors.notFound()

    @ExceptionHandler(AttendanceConflict::class)
    fun conflict(exception: AttendanceConflict) = FeatureHttpErrors.conflict(exception.currentRevision)

    @ExceptionHandler(AttendanceIdempotencyConflict::class)
    fun idempotencyConflict() = FeatureHttpErrors.idempotencyConflict()

    @ExceptionHandler(InvalidAttendanceRecords::class)
    fun invalidAttendanceRecords() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Attendance includes a student outside this class session"))
}
