package br.com.ajadigital.core.academic
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.CreateClassDto
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.application.usecases.CreateClassUseCase
import br.com.ajadigital.core.academic.application.usecases.ListMyEnrollmentsUseCase
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Instant
import br.com.ajadigital.core.shared.application.contracts.Page

class AcademicApplicationUseCaseTests {
    @Test
    fun `authorized admin creates class through application input port`() {
        var created = false
        val store = proxy<AcademicStoreOutputPort> { method, _ ->
            when (method) {
                "isCurrentAdminSession" -> true
                "createClass" -> { created = true; AcademicClassView("class-1", 1, Instant.EPOCH, Instant.EPOCH, "admin-1", "Math", "MATH-1", 2026, "Manhã", true, emptyList()) }
                else -> error("Unexpected academic-store call: $method")
            }
        }
        val useCase = createClass(store)

        val result = useCase.createClass(CreateClassDto(actor("admin"), "Math", "MATH-1", 2026, "Manhã", "create-class-123"))

        assertEquals("class-1", result.id)
        assertEquals(true, created)
    }

    @Test
    fun `authorization precedes invalid class transition input at application boundary`() {
        val store = proxy<AcademicStoreOutputPort> { method, _ -> error("Unauthorized actor must be rejected before $method") }

        assertThrows(AcademicForbidden::class.java) {
            createClass(store).createClass(CreateClassDto(actor("student"), " ", "", 0, "invalid", "create-class-123"))
        }
    }

    @Test
    fun `authorized application rejects invalid class fields before persistence`() {
        val store = proxy<AcademicStoreOutputPort> { method, _ ->
            when (method) {
                "isCurrentAdminSession" -> true
                else -> error("Invalid class fields must be rejected before $method")
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            createClass(store).createClass(CreateClassDto(actor("admin"), " ", "", 0, "invalid", "create-class-123"))
        }
    }

    @Test
    fun `pure academic authorization policy depends only on actor facts`() {
        AcademicPolicy.requireAdmin("admin", true)
        assertThrows(AcademicForbidden::class.java) {
            AcademicPolicy.requireAdmin("admin", false)
        }
    }

    @Test
    fun `profile enrollment query follows the current user for each documented role`() {
        val queries = mutableListOf<AcademicStoreMyEnrollmentsDto>()
        val store = proxy<AcademicStoreOutputPort> { method, args ->
            when (method) {
                "myEnrollments" -> {
                    queries += args[0] as AcademicStoreMyEnrollmentsDto
                    Page<AcademicEnrollmentView>(emptyList(), 1, 20, 0)
                }
                else -> error("Unexpected academic-store call: $method")
            }
        }
        val useCase = ListMyEnrollmentsUseCase(store)

        for (role in listOf("student", "teacher", "admin")) {
            useCase.myEnrollments(ListMyEnrollmentsDto(actor(role, "user-$role"), 1, 20, "id"))
        }

        assertEquals(listOf("user-student", "user-teacher", "user-admin"), queries.map { it.studentId })
    }

    @Test
    fun `academic output projections snapshot collection aliases`() {
        val ids = mutableListOf("student-1")
        val assessments = mutableListOf("assessment-1")
        val classView = AcademicClassView("class-1", 1, Instant.EPOCH, Instant.EPOCH, "admin", "Math", "MATH", 2026, "Manhã", true, ids)
        val enrollment = AcademicEnrollmentView("enrollment-1", 1, Instant.EPOCH, Instant.EPOCH, "admin", "class-1", "a@example.test", null, "pending", "admin", null, null, false, assessments, "Math")
        ids += "student-2"
        assessments += "assessment-2"
        assertEquals(listOf("student-1"), classView.studentIds)
        assertEquals(listOf("assessment-1"), enrollment.unassignedAssessmentIds)
        assertThrows(UnsupportedOperationException::class.java) { (classView.studentIds as MutableList<String>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (enrollment.unassignedAssessmentIds as MutableList<String>).clear() }
        val source = mutableListOf(enrollment)
        val student = AcademicStudentView(AcademicStudentSummaryView("student-1", "A", "a@example.test", true, enrollment), source)
        source.clear()
        assertEquals(1, student.enrollments.size)
        assertThrows(UnsupportedOperationException::class.java) { (student.enrollments as MutableList<AcademicEnrollmentView>).clear() }
        assertThrows(IllegalArgumentException::class.java) { Page<AcademicClassView>(emptyList(), 0, 20, 0) }
    }

    private fun createClass(store: AcademicStoreOutputPort): CreateClassUseCase = CreateClassUseCase(
        store,
        proxy<AcademicRequestFingerprintOutputPort> { method, _ -> if (method == "createClass") "class-request" else error("Unexpected fingerprint call: $method") },
        proxy<EncodedIdempotencyStoreOutputPort> { method, _ -> when (method) { "findEncoded" -> null; "saveEncoded" -> null; else -> error("Unexpected idempotency call: $method") } },
        proxy<AcademicReceiptCodecOutputPort> { method, _ -> when (method) { "encode" -> "receipt"; "decodeClass" -> error("Unexpected receipt replay"); else -> error("Unexpected receipt codec call: $method") } },
        object : TransactionRunnerOutputPort {
            override fun <T> run(block: () -> T): T = block()
        },
    )

    private fun actor(role: String, id: String = "user-1") = AuthenticatedActor(id, role, true, Instant.EPOCH, "session-reference")

    private inline fun <reified T> proxy(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            if (method.declaringClass == Any::class.java) return@newProxyInstance method.name
            answer(method.name, args ?: emptyArray())
        } as T
}
