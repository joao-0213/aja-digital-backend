package br.com.ajadigital.core

import br.com.ajadigital.core.application.identity.RegisterStudent
import br.com.ajadigital.core.application.identity.RegistrationCommand
import br.com.ajadigital.core.application.identity.RegistrationConflict
import br.com.ajadigital.core.application.identity.AuthenticateStudent
import br.com.ajadigital.core.application.identity.InvalidCredentials
import br.com.ajadigital.core.application.identity.LoginCommand
import br.com.ajadigital.core.application.identity.SessionRequired
import br.com.ajadigital.core.application.identity.toResponse
import br.com.ajadigital.core.application.identity.RequestAuthToken
import br.com.ajadigital.core.application.identity.TokenCommand
import br.com.ajadigital.core.application.identity.AuthTokenInvalid
import br.com.ajadigital.core.application.identity.AuthTokenExpired
import br.com.ajadigital.core.application.identity.ResetPassword
import br.com.ajadigital.core.application.identity.PasswordResetCommand
import br.com.ajadigital.core.application.identity.UpdateOwnProfile
import br.com.ajadigital.core.application.identity.ProfileEditCommand
import br.com.ajadigital.core.application.identity.ProfileRevisionConflict
import br.com.ajadigital.core.domain.identity.User
import br.com.ajadigital.core.domain.identity.EmailAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import java.util.UUID
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import java.time.Duration
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import org.springframework.web.bind.annotation.RequestHeader
import jakarta.servlet.http.HttpServletRequest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

data class RegisterRequest(val name: String, val email: String, val password: String)

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val registerStudent: RegisterStudent,
    private val authenticateStudent: AuthenticateStudent,
    private val requestAuthToken: RequestAuthToken,
    private val resetPassword: ResetPassword,
    @Value("\${aja.cookie.secure:false}") private val cookieSecure: Boolean,
) {
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<User> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            registerStudent.register(RegistrationCommand(request.name, request.email, request.password)).user,
        )

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        @CookieValue(name = "aja_session", required = false) previousCookie: String?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        require(EmailAddress.isValid(request.email.trim()))
        return try {
            val authenticated = authenticateStudent.login(LoginCommand(request.email, request.password, previousCookie))
            ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(authenticated.cookieValue, Duration.ofHours(8), servletRequest.isSecure).toString())
                .body(authenticated.session.toResponse())
        } catch (_: InvalidCredentials) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, servletRequest.isSecure).toString())
                .body(errorBody("INVALID_CREDENTIALS", "Email or password is incorrect"))
        }
    }

    @GetMapping("/session")
    fun session(@CookieValue(name = "aja_session", required = false) cookie: String?) =
        authenticateStudent.resolve(cookie).toResponse()

    @PostMapping("/logout")
    fun logout(@CookieValue(name = "aja_session", required = false) cookie: String?, servletRequest: HttpServletRequest): ResponseEntity<Void> {
        authenticateStudent.logout(cookie)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, servletRequest.isSecure).toString())
            .build()
    }

    @PostMapping("/email-confirmation-requests")
    fun requestEmailConfirmation(@RequestBody request: EmailRequest) =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(requestAuthToken.request(request.email, "email_confirmation"))

    @PostMapping("/email-confirmations")
    fun confirmEmail(@RequestBody request: TokenCommand): User = requestAuthToken.confirmEmail(request.token)

    @PostMapping("/password-reset-requests")
    fun requestPasswordReset(@RequestBody request: EmailRequest) =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(requestAuthToken.request(request.email, "password_reset"))

    @PostMapping("/password-resets")
    fun resetPassword(@RequestBody request: PasswordResetCommand) = resetPassword.reset(request)

    private fun sessionCookie(value: String, maxAge: Duration, isSecureRequest: Boolean): ResponseCookie =
        createSessionCookie(value, maxAge, cookieSecure || isSecureRequest)

    private fun errorBody(code: String, message: String) = mapOf(
        "error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString()),
    )
}

data class LoginRequest(val email: String, val password: String)
data class EmailRequest(val email: String)

internal fun createSessionCookie(value: String, maxAge: Duration, secure: Boolean): ResponseCookie =
    ResponseCookie.from("aja_session", value)
        .httpOnly(true)
        .secure(secure)
        .path("/api/v1")
        .sameSite("Lax")
        .maxAge(maxAge)
        .build()

@RestController
class SelfController(private val authenticateStudent: AuthenticateStudent) {
    @GetMapping("/api/v1/me")
    fun me(@CookieValue(name = "aja_session", required = false) cookie: String?): User =
        authenticateStudent.resolve(cookie).user
}

@RestController
class ProfileController(private val updateOwnProfile: UpdateOwnProfile) {
    @PatchMapping("/api/v1/me")
    fun update(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestBody command: ProfileEditRequest,
    ): User {
        require(command.expectedRevision.isIntegralNumber && command.expectedRevision.canConvertToInt())
        val expectedRevision = command.expectedRevision.intValue()
        require(expectedRevision > 0)
        require(command.name.isMissingNode || command.name.isTextual)
        val name = command.name.takeUnless { it.isMissingNode }?.asText()
        return updateOwnProfile.update(cookie, ProfileEditCommand(expectedRevision, name))
    }
}

class InvalidInternalServiceCredential : RuntimeException()
data class InternalSessionResponse(val userId: String, val sessionId: String, val role: String, val active: Boolean, val emailConfirmedAt: java.time.Instant?)

@RestController
class InternalSessionController(
    private val authenticateStudent: AuthenticateStudent,
    @Value("\${core.internal.questions-token}") serviceToken: String,
) {
    private val expectedToken = serviceToken.toByteArray(StandardCharsets.UTF_8)

    init {
        require(expectedToken.size >= 32) { "Questions service credential must contain at least 32 bytes" }
    }

    @GetMapping("/internal/v1/session")
    fun inspect(
        @RequestHeader(name = "X-AJA-Service-Token", required = false) candidateToken: String?,
        @CookieValue(name = "aja_session", required = false) cookie: String?,
    ): InternalSessionResponse {
        val supplied = candidateToken?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (!MessageDigest.isEqual(expectedToken, supplied)) throw InvalidInternalServiceCredential()
        val session = authenticateStudent.resolve(cookie)
        return InternalSessionResponse(session.user.id, session.id, session.user.role, session.user.active, session.user.emailConfirmedAt)
    }
}

@JsonIgnoreProperties(ignoreUnknown = false)
data class ProfileEditRequest(
    val expectedRevision: JsonNode = MissingNode.getInstance(),
    val name: JsonNode = MissingNode.getInstance(),
)

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

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid() = ResponseEntity.unprocessableEntity().body(error("VALIDATION_ERROR", "Request is invalid"))

    @ExceptionHandler(DataAccessException::class)
    fun unavailable() = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("PERSISTENCE_FAILED", "Request could not be completed"))

    private fun error(code: String, message: String) = mapOf("error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString()))
}
