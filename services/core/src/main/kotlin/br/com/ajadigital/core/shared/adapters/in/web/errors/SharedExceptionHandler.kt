package br.com.ajadigital.core.shared.adapters.`in`.web.errors

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class SharedExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException::class, IllegalStateException::class)
    fun stateConflict() = ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("BUSINESS_CONFLICT", "Resource conflicts with current state"))
    @ExceptionHandler(NoSuchElementException::class)
    fun missing() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))
}
