package br.com.ajadigital.core.dashboard.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.dashboard.domain.exceptions.DashboardForbidden
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class DashboardExceptionHandler {
    @ExceptionHandler(DashboardForbidden::class)
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "This action is not allowed"))
}
