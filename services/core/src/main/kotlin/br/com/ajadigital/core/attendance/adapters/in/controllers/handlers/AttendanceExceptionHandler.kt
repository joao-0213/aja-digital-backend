package br.com.ajadigital.core.attendance.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.attendance.domain.exceptions.InvalidAttendanceRecords
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
class AttendanceExceptionHandler {
    @ExceptionHandler(InvalidAttendanceRecords::class)
    fun invalidAttendanceRecords() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Attendance includes a student outside this class session"))
}
