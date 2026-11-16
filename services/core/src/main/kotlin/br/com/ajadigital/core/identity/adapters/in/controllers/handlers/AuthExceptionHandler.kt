package br.com.ajadigital.core.identity.adapters.`in`.controllers.handlers
import br.com.ajadigital.core.identity.domain.exceptions.InvalidInternalServiceCredential

import br.com.ajadigital.core.identity.domain.exceptions.RegistrationConflict
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenInvalid
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenExpired
import br.com.ajadigital.core.identity.domain.exceptions.ProfileRevisionConflict
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import java.util.UUID


@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", "Resource was not found"))

    @ExceptionHandler(RegistrationConflict::class)
    fun duplicate() = ResponseEntity.status(HttpStatus.CONFLICT).body(error("VALIDATION_ERROR", "Email is already registered"))

    @ExceptionHandler(DuplicateKeyException::class)
    fun concurrentDuplicate() = ResponseEntity.status(HttpStatus.CONFLICT).body(error("VALIDATION_ERROR", "Email is already registered"))

    @ExceptionHandler(SessionRequired::class)
    fun sessionRequired() = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("SESSION_REQUIRED", "A valid session is required"))

    @ExceptionHandler(InvalidInternalServiceCredential::class)
    fun invalidInternalCredential() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", "Service credential is invalid"))

    @ExceptionHandler(AuthTokenInvalid::class)
    fun tokenInvalid() = ResponseEntity.unprocessableEntity().body(error("TOKEN_INVALID", "Token is invalid"))

    @ExceptionHandler(AuthTokenExpired::class)
    fun tokenExpired() = ResponseEntity.unprocessableEntity().body(error("TOKEN_EXPIRED", "Token has expired"))

    @ExceptionHandler(ProfileRevisionConflict::class)
    fun revisionConflict(exception: ProfileRevisionConflict) = ResponseEntity.status(HttpStatus.CONFLICT).body(
        mapOf("error" to mapOf("code" to "REVISION_CONFLICT", "message" to "Profile changed since it was read", "requestId" to UUID.randomUUID().toString(), "details" to mapOf("currentRevision" to exception.currentRevision))),
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Request is invalid"))

    @ExceptionHandler(
        MissingRequestHeaderException::class,
        MethodArgumentTypeMismatchException::class,
        MethodArgumentNotValidException::class,
        HandlerMethodValidationException::class,
    )
    fun invalidParameter() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Request is invalid"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Request is invalid"))

    @ExceptionHandler(DataAccessException::class)
    fun unavailable() = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("PERSISTENCE_FAILED", "Request could not be completed"))

    private fun error(code: String, message: String) = mapOf("error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString()))
}
