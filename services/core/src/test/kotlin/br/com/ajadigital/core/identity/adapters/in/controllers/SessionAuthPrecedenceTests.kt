package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.classroom.adapters.`in`.controllers.ClassroomController
import br.com.ajadigital.core.classroom.domain.ports.`in`.ManageAcademicsUseCase
import br.com.ajadigital.core.identity.adapters.`in`.controllers.handlers.AuthExceptionHandler
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.identity.domain.models.AuthenticatedSession
import br.com.ajadigital.core.identity.domain.models.LoginCommand
import br.com.ajadigital.core.identity.domain.ports.`in`.AuthenticateStudentUseCase
import br.com.ajadigital.core.identity.domain.ports.out.SessionRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.lang.reflect.Proxy

class SessionAuthPrecedenceTests {
    private lateinit var mvc: MockMvc

    private val stubAuth = object : AuthenticateStudentUseCase {
        override fun login(command: LoginCommand): AuthenticatedSession = error("Not used")
        override fun resolve(cookieValue: String?): SessionRecord = throw SessionRequired()
        override fun logout(cookieValue: String?) {}
    }

    private val unusedAcademics = Proxy.newProxyInstance(
        ManageAcademicsUseCase::class.java.classLoader,
        arrayOf(ManageAcademicsUseCase::class.java),
    ) { _, _, _ -> error("Should not reach use case when session is invalid") } as ManageAcademicsUseCase

    @BeforeEach
    fun setup() {
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
        mvc = MockMvcBuilders
            .standaloneSetup(ClassroomController(stubAuth, unusedAcademics))
            .setControllerAdvice(AuthExceptionHandler())
            .setValidator(validator)
            .build()
    }

    @Test
    fun createClassWithoutSessionReturns401BeforeBodyValidation() {
        mvc.post("/api/v1/classes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"","code":"","year":0,"shift":""}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error.code") { value("SESSION_REQUIRED") }
        }
    }
}
