package br.com.ajadigital.core.academic

import br.com.ajadigital.core.academic.application.contracts.DashboardClassProjection
import br.com.ajadigital.core.academic.application.contracts.DashboardAcademicQuery
import br.com.ajadigital.core.academic.application.models.AcademicPage
import br.com.ajadigital.core.academic.application.ports.`in`.CountMyPendingEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListDashboardClassesInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort
import br.com.ajadigital.core.academic.application.usecases.GetDashboardAcademicUseCase
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Instant

class GetDashboardAcademicUseCaseTests {
    @Test
    fun `staff reads typed bounded classes then enrollment and student counts`() {
        val calls = mutableListOf<String>()
        val projection = DashboardClassProjection("class-1", 2, Instant.EPOCH, Instant.EPOCH, "admin-1", "Class", "C1", 2026, "Manhã", true, 3)
        val useCase = GetDashboardAcademicUseCase(
            proxy<ListDashboardClassesInputPort> { name, args -> calls += name; assertEquals(100, (args[0] as br.com.ajadigital.core.academic.application.models.ListDashboardClassesDto).pageSize); listOf(projection) },
            proxy<ListEnrollmentsInputPort> { name, _ -> calls += name; AcademicPage(emptyList(), 1, 1, 4) },
            proxy<ListStudentsInputPort> { name, _ -> calls += name; AcademicPage(emptyList(), 1, 1, 8) },
            proxy<CountMyPendingEnrollmentsInputPort> { name, _ -> error("Unexpected $name") },
        )

        val result = useCase.dashboard(DashboardAcademicQuery(actor("admin")))

        assertEquals(listOf("dashboardClasses", "enrollments", "students"), calls)
        assertEquals(listOf(projection), result.classes)
        assertEquals(4, result.pendingEnrollmentCount)
        assertEquals(8, result.studentCount)
        assertThrows(UnsupportedOperationException::class.java) { (result.classes as MutableList<DashboardClassProjection>).clear() }
    }

    @Test
    fun `staff source failure stops later academic reads`() {
        val useCase = GetDashboardAcademicUseCase(
            proxy<ListDashboardClassesInputPort> { _, _ -> throw IllegalStateException("class source failed") },
            proxy<ListEnrollmentsInputPort> { name, _ -> error("Unexpected $name") },
            proxy<ListStudentsInputPort> { name, _ -> error("Unexpected $name") },
            proxy<CountMyPendingEnrollmentsInputPort> { name, _ -> error("Unexpected $name") },
        )

        val error = assertThrows(IllegalStateException::class.java) { useCase.dashboard(DashboardAcademicQuery(actor("teacher"))) }
        assertEquals("class source failed", error.message)
    }

    @Test
    fun `student reads only own pending enrollment count`() {
        val useCase = GetDashboardAcademicUseCase(
            proxy<ListDashboardClassesInputPort> { name, _ -> error("Unexpected $name") },
            proxy<ListEnrollmentsInputPort> { name, _ -> error("Unexpected $name") },
            proxy<ListStudentsInputPort> { name, _ -> error("Unexpected $name") },
            proxy<CountMyPendingEnrollmentsInputPort> { name, _ -> if (name == "myPendingEnrollmentCount") 2 else error("Unexpected $name") },
        )

        val result = useCase.dashboard(DashboardAcademicQuery(actor("student")))
        assertEquals(2, result.pendingEnrollmentCount)
        assertEquals(emptyList<DashboardClassProjection>(), result.classes)
    }

    private fun actor(role: String) = AuthenticatedActor("user-1", role, true, Instant.EPOCH, "session-ref")

    private inline fun <reified T> proxy(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            if (method.declaringClass == Any::class.java) return@newProxyInstance method.name
            answer(method.name, args ?: emptyArray())
        } as T
}
