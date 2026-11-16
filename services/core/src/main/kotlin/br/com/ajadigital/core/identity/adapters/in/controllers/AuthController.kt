package br.com.ajadigital.core.identity.adapters.`in`.controllers
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.EmailRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.LoginRequest
import br.com.ajadigital.core.identity.adapters.`in`.controllers.models.requests.RegisterRequest

import br.com.ajadigital.core.identity.domain.usecases.RegisterStudent
import br.com.ajadigital.core.identity.domain.models.RegistrationCommand
import br.com.ajadigital.core.identity.domain.usecases.AuthenticateStudent
import br.com.ajadigital.core.identity.domain.exceptions.InvalidCredentials
import br.com.ajadigital.core.identity.domain.models.LoginCommand
import br.com.ajadigital.core.identity.domain.usecases.toResponse
import br.com.ajadigital.core.identity.domain.usecases.RequestAuthToken
import br.com.ajadigital.core.identity.domain.models.TokenCommand
import br.com.ajadigital.core.identity.domain.usecases.ResetPassword
import br.com.ajadigital.core.identity.domain.models.PasswordResetCommand
import br.com.ajadigital.core.identity.domain.models.User
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
