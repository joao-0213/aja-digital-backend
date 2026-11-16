package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.classroom.adapters.`in`.controllers.ClassroomController
import br.com.ajadigital.core.classroom.domain.ports.`in`.CloseClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.DeactivateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetStudentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListMyEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListStudentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListUsersUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.PreviewEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ReopenClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.SetUserRoleUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.TransitionEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.WithdrawUserUseCase
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

    private inline fun <reified T> unusedPort(): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { _, _, _ -> error("Should not reach use case when session is invalid") } as T

    @BeforeEach
    fun setup() {
        val validator = LocalValidatorFactoryBean().apply { afterPropertiesSet() }
        mvc = MockMvcBuilders
            .standaloneSetup(
                ClassroomController(
                    stubAuth,
                    unusedPort<ListUsersUseCase>(),
                    unusedPort<GetUserUseCase>(),
                    unusedPort<CreateUserUseCase>(),
                    unusedPort<UpdateUserUseCase>(),
                    unusedPort<SetUserRoleUseCase>(),
                    unusedPort<DeactivateUserUseCase>(),
                    unusedPort<WithdrawUserUseCase>(),
                    unusedPort<ListClassesUseCase>(),
                    unusedPort<GetClassUseCase>(),
                    unusedPort<CreateClassUseCase>(),
                    unusedPort<UpdateClassUseCase>(),
                    unusedPort<CloseClassUseCase>(),
                    unusedPort<ReopenClassUseCase>(),
                    unusedPort<ListEnrollmentsUseCase>(),
                    unusedPort<GetEnrollmentUseCase>(),
                    unusedPort<PreviewEnrollmentUseCase>(),
                    unusedPort<CreateEnrollmentUseCase>(),
                    unusedPort<TransitionEnrollmentUseCase>(),
                    unusedPort<ListMyEnrollmentsUseCase>(),
                    unusedPort<ListStudentsUseCase>(),
                    unusedPort<GetStudentUseCase>(),
                ),
            )
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
