package br.com.ajadigital.core.identity.adapters.`in`.controllers
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.EmailRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.LoginRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.PasswordResetRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.RegisterRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.TokenRequest
import jakarta.validation.Valid

import br.com.ajadigital.core.identity.application.ports.`in`.RegisterStudentInputPort
import br.com.ajadigital.core.identity.application.models.RegistrationCommand
import br.com.ajadigital.core.identity.application.ports.`in`.LoginInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.LogoutInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import br.com.ajadigital.core.identity.domain.exceptions.InvalidCredentials
import br.com.ajadigital.core.identity.application.models.LoginCommand
import br.com.ajadigital.core.identity.adapters.`in`.controllers.mappers.SessionResponseMapper
import br.com.ajadigital.core.identity.application.ports.`in`.RequestAuthTokenInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResetPasswordInputPort
import br.com.ajadigital.core.identity.application.models.PasswordResetCommand
import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.application.models.RegisterStudentDto
import br.com.ajadigital.core.identity.application.models.LogoutStudentSessionDto
import br.com.ajadigital.core.identity.application.models.RequestAuthTokenDto
import br.com.ajadigital.core.identity.application.models.ConfirmEmailTokenDto
import br.com.ajadigital.core.identity.application.models.ResetStudentPasswordDto
import br.com.ajadigital.core.identity.domain.policies.EmailAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import java.time.Duration
import jakarta.servlet.http.HttpServletRequest


@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val registerStudent: RegisterStudentInputPort,
    private val login: LoginInputPort,
    private val resolveSession: ResolveSessionInputPort,
    private val logoutSession: LogoutInputPort,
    private val requestAuthToken: RequestAuthTokenInputPort,
    private val resetPassword: ResetPasswordInputPort,
    @Value("\${aja.cookie.secure:false}") private val cookieSecure: Boolean,
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<User> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            registerStudent.register(RegisterStudentDto(RegistrationCommand(request.name, request.email, request.password))).user,
        )

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        @CookieValue(name = "aja_session", required = false) previousCookie: String?,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        require(EmailAddress.isValid(request.email.trim()))
        return try {
            val authenticated = login.login(LoginCommand(request.email, request.password, previousCookie))
            ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie(authenticated.cookieValue, Duration.ofHours(8), servletRequest.isSecure).toString())
                .body(SessionResponseMapper.map(authenticated.session))
        } catch (_: InvalidCredentials) {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, servletRequest.isSecure).toString())
                .body(errorBody("INVALID_CREDENTIALS", "Email or password is incorrect"))
        }
    }

    @GetMapping("/session")
    fun session(@CookieValue(name = "aja_session", required = false) cookie: String?) =
        SessionResponseMapper.map(resolveSession.resolve(cookie))

    @PostMapping("/logout")
    fun logout(@CookieValue(name = "aja_session", required = false) cookie: String?, servletRequest: HttpServletRequest): ResponseEntity<Void> {
        logoutSession.logout(LogoutStudentSessionDto(cookie))
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, sessionCookie("", Duration.ZERO, servletRequest.isSecure).toString())
            .build()
    }

    @PostMapping("/email-confirmation-requests")
    fun requestEmailConfirmation(@Valid @RequestBody request: EmailRequest) =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(requestAuthToken.request(RequestAuthTokenDto(request.email, "email_confirmation")))

    @PostMapping("/email-confirmations")
    fun confirmEmail(@Valid @RequestBody request: TokenRequest): User = requestAuthToken.confirmEmail(ConfirmEmailTokenDto(request.token))

    @PostMapping("/password-reset-requests")
    fun requestPasswordReset(@Valid @RequestBody request: EmailRequest) =
        ResponseEntity.status(HttpStatus.ACCEPTED).body(requestAuthToken.request(RequestAuthTokenDto(request.email, "password_reset")))

    @PostMapping("/password-resets")
    fun resetPassword(@Valid @RequestBody request: PasswordResetRequest) =
        resetPassword.reset(ResetStudentPasswordDto(PasswordResetCommand(request.token, request.password)))

    private fun sessionCookie(value: String, maxAge: Duration, isSecureRequest: Boolean): ResponseCookie =
        createSessionCookie(value, maxAge, cookieSecure || isSecureRequest)

    private fun errorBody(code: String, message: String) = mapOf(
        "error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString()),
    )
}
