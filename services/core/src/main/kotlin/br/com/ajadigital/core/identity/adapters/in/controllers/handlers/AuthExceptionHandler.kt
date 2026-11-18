package br.com.ajadigital.core.identity.adapters.`in`.controllers.handlers

import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenExpired
import br.com.ajadigital.core.identity.domain.exceptions.AuthTokenInvalid
import br.com.ajadigital.core.identity.domain.exceptions.InvalidInternalServiceCredential
import br.com.ajadigital.core.identity.domain.exceptions.ProfileRevisionConflict
import br.com.ajadigital.core.identity.domain.exceptions.RegistrationConflict
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.errorBody
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(RegistrationConflict::class)
    fun duplicate() = ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("VALIDATION_ERROR", "Email is already registered"))

    @ExceptionHandler(DuplicateKeyException::class)
    fun concurrentDuplicate() = ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody("VALIDATION_ERROR", "Email is already registered"))

    @ExceptionHandler(SessionRequired::class)
    fun sessionRequired() = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody("SESSION_REQUIRED", "A valid session is required"))

    @ExceptionHandler(InvalidInternalServiceCredential::class)
    fun invalidInternalCredential() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "Service credential is invalid"))

    @ExceptionHandler(AuthTokenInvalid::class)
    fun tokenInvalid() = ResponseEntity.unprocessableEntity().body(errorBody("TOKEN_INVALID", "Token is invalid"))

    @ExceptionHandler(AuthTokenExpired::class)
    fun tokenExpired() = ResponseEntity.unprocessableEntity().body(errorBody("TOKEN_EXPIRED", "Token has expired"))

    @ExceptionHandler(ProfileRevisionConflict::class)
    fun revisionConflict(exception: ProfileRevisionConflict) = ResponseEntity.status(HttpStatus.CONFLICT).body(
        mapOf("error" to mapOf("code" to "REVISION_CONFLICT", "message" to "Profile changed since it was read", "requestId" to UUID.randomUUID().toString(), "details" to mapOf("currentRevision" to exception.currentRevision))),
    )
}
