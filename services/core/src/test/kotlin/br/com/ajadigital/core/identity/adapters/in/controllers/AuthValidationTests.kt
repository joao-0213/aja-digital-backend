package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.identity.adapters.`in`.controllers.handlers.AuthExceptionHandler
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.SharedExceptionHandler
import br.com.ajadigital.core.identity.domain.exceptions.InvalidCredentials
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.identity.application.models.AcceptedResponse
import br.com.ajadigital.core.identity.application.models.AuthenticatedSession
import br.com.ajadigital.core.identity.application.models.LoginCommand
import br.com.ajadigital.core.identity.application.models.PasswordResetCommand
import br.com.ajadigital.core.identity.application.models.RegistrationCommand
import br.com.ajadigital.core.identity.application.models.RegistrationResult
import br.com.ajadigital.core.identity.domain.models.User
import br.com.ajadigital.core.identity.domain.models.SessionRecord
import br.com.ajadigital.core.identity.domain.models.*
import br.com.ajadigital.core.identity.application.models.*
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveSessionInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.LoginInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.LogoutInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.RequestAuthTokenInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.ResetPasswordInputPort
import br.com.ajadigital.core.identity.application.ports.`in`.RegisterStudentInputPort
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

    private val stubRegister = object : RegisterStudentInputPort {
        override fun register(request: RegisterStudentDto): RegistrationResult = error("Should not reach use case on invalid DTO")
    }
    private val stubLogin = object : LoginInputPort {
        override fun login(request: LoginStudentDto): AuthenticatedSession = throw InvalidCredentials()
    }
    private val stubAuth = object : ResolveSessionInputPort {
        override fun resolve(request: ResolveStudentSessionDto): SessionRecord = throw SessionRequired()
    }
    private val stubLogout = object : LogoutInputPort {
        override fun logout(request: LogoutStudentSessionDto) {}
    }
    private val stubTokens = object : RequestAuthTokenInputPort {
        override fun request(request: RequestAuthTokenDto): AcceptedResponse = error("Should not reach use case")
        override fun confirmEmail(request: ConfirmEmailTokenDto): User = error("Should not reach use case")
    }
    private val stubReset = object : ResetPasswordInputPort {
        override fun reset(request: ResetStudentPasswordDto): AcceptedResponse = error("Should not reach use case")
    }

    @BeforeEach
    fun setup() {
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
        mvc = MockMvcBuilders
            .standaloneSetup(AuthController(stubRegister, stubLogin, stubAuth, stubLogout, stubTokens, stubReset, false))
            .setControllerAdvice(AuthExceptionHandler(), SharedExceptionHandler())
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
