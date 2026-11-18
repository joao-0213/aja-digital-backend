package br.com.ajadigital.core.identity.adapters.`in`.controllers

import br.com.ajadigital.core.academic.adapters.`in`.controllers.AcademicController
import br.com.ajadigital.core.academic.application.ports.`in`.CloseClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.DeactivateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetStudentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListClassesInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListMyEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListUsersInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.PreviewEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ReopenClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.SetUserRoleInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.TransitionEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.WithdrawUserInputPort
import br.com.ajadigital.core.identity.adapters.`in`.controllers.handlers.AuthExceptionHandler
import br.com.ajadigital.core.shared.adapters.`in`.web.errors.SharedExceptionHandler
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto
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

    private val stubAuth = object : ResolveAuthenticatedActorInputPort {
        override fun resolve(request: ResolveStudentSessionDto): AuthenticatedActor = throw SessionRequired()
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
                AcademicController(
                    stubAuth,
                    unusedPort<ListUsersInputPort>(),
                    unusedPort<GetUserInputPort>(),
                    unusedPort<CreateUserInputPort>(),
                    unusedPort<UpdateUserInputPort>(),
                    unusedPort<SetUserRoleInputPort>(),
                    unusedPort<DeactivateUserInputPort>(),
                    unusedPort<WithdrawUserInputPort>(),
                    unusedPort<ListClassesInputPort>(),
                    unusedPort<GetClassInputPort>(),
                    unusedPort<CreateClassInputPort>(),
                    unusedPort<UpdateClassInputPort>(),
                    unusedPort<CloseClassInputPort>(),
                    unusedPort<ReopenClassInputPort>(),
                    unusedPort<ListEnrollmentsInputPort>(),
                    unusedPort<GetEnrollmentInputPort>(),
                    unusedPort<PreviewEnrollmentInputPort>(),
                    unusedPort<CreateEnrollmentInputPort>(),
                    unusedPort<TransitionEnrollmentInputPort>(),
                    unusedPort<ListMyEnrollmentsInputPort>(),
                    unusedPort<ListStudentsInputPort>(),
                    unusedPort<GetStudentInputPort>(),
                ),
            )
            .setControllerAdvice(AuthExceptionHandler(), SharedExceptionHandler())
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
