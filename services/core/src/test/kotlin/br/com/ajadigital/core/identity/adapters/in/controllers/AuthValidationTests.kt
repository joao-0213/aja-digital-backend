package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.identity.adapters.`in`.controllers.handlers.AuthExceptionHandler
import br.com.ajadigital.core.identity.domain.exceptions.InvalidCredentials
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.identity.domain.models.AcceptedResponse
import br.com.ajadigital.core.identity.domain.models.AuthenticatedSession
import br.com.ajadigital.core.identity.domain.models.LoginCommand
import br.com.ajadigital.core.identity.domain.models.PasswordResetCommand
import br.com.ajadigital.core.identity.domain.models.RegistrationCommand
import br.com.ajadigital.core.identity.domain.models.RegistrationResult
import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.domain.ports.out.SessionRecord
import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.RequestAuthTokenUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.ResetPasswordUseCase
import br.com.ajadigital.core.identity.domain.ports.`in`.RegisterStudentUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

class AuthValidationTests {
    private lateinit var mvc: MockMvc

    private val stubRegister = object : RegisterStudentUseCase {
        override fun register(command: RegistrationCommand): RegistrationResult = error("Should not reach use case on invalid DTO")
    }
    private val stubAuth = object : AuthenticateStudentUseCase {
        override fun login(command: LoginCommand): AuthenticatedSession = throw InvalidCredentials()
        override fun resolve(cookieValue: String?): SessionRecord = throw SessionRequired()
        override fun logout(cookieValue: String?) {}
    }
    private val stubTokens = object : RequestAuthTokenUseCase {
        override fun request(email: String, kind: String): AcceptedResponse = error("Should not reach use case")
        override fun confirmEmail(token: String): User = error("Should not reach use case")
    }
    private val stubReset = object : ResetPasswordUseCase {
        override fun reset(command: PasswordResetCommand): AcceptedResponse = error("Should not reach use case")
    }

    @BeforeEach
    fun setup() {
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
        mvc = MockMvcBuilders
            .standaloneSetup(AuthController(stubRegister, stubAuth, stubTokens, stubReset, false))
            .setControllerAdvice(AuthExceptionHandler())
            .setValidator(validator)
            .build()
    }

    @Test
    fun registerRejectsBlankFieldsWith422ValidationError() {
        mvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":" ","email":"","password":""}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Request is invalid") }
            jsonPath("$.error.requestId") { isNotEmpty() }
        }
    }

    @Test
    fun loginRejectsBlankEmailWith422ValidationErrorAndPreservesBlankPassword401() {
        mvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":" ","password":"secret"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Request is invalid") }
            jsonPath("$.error.requestId") { isNotEmpty() }
        }
        mvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"student@example.test","password":" "}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun emailConfirmationRequestRejectsBlankEmailWith422ValidationError() {
        mvc.post("/api/v1/auth/email-confirmation-requests") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":" "}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Request is invalid") }
        }
    }

    @Test
    fun emailConfirmationRejectsBlankTokenWith422ValidationError() {
        mvc.post("/api/v1/auth/email-confirmations") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":" "}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Request is invalid") }
        }
    }

    @Test
    fun passwordResetRequestRejectsBlankEmailWith422ValidationError() {
        mvc.post("/api/v1/auth/password-reset-requests") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":""}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Request is invalid") }
        }
    }

    @Test
    fun passwordResetRejectsBlankFieldsWith422ValidationError() {
        mvc.post("/api/v1/auth/password-resets") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token":"","password":" "}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("VALIDATION_ERROR") }
            jsonPath("$.error.message") { value("Request is invalid") }
        }
    }

    @Test
    fun sessionWithoutCookieReturns401SessionRequired() {
        mvc.get("/api/v1/auth/session")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.error.code") { value("SESSION_REQUIRED") }
            }
    }
}
