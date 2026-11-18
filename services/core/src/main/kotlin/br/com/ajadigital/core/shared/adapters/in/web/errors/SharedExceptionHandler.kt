package br.com.ajadigital.core.shared.adapters.`in`.web.errors

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class SharedExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException::class, IllegalStateException::class)
    fun stateConflict() = ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("BUSINESS_CONFLICT", "Resource conflicts with current state"))

    @ExceptionHandler(NoSuchElementException::class, NoResourceFoundException::class, NoHandlerFoundException::class)
    fun missing() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingRequestHeaderException::class,
        MethodArgumentTypeMismatchException::class,
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
        IllegalArgumentException::class,
    )
    fun invalidRequest() = ResponseEntity.unprocessableEntity().body(errorBody("VALIDATION_ERROR", "Request is invalid"))

    @ExceptionHandler(DataAccessException::class)
    fun unavailable() = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody("PERSISTENCE_FAILED", "Request could not be completed"))
}
