package br.com.ajadigital.core.academic
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.CreateClassDto
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.application.usecases.CreateClassUseCase
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.time.Instant

class AcademicApplicationUseCaseTests {
    @Test
    fun `authorized admin creates class through application input port`() {
        var created = false
        val store = proxy<AcademicStoreOutputPort> { method, _ ->
            when (method) {
                "isCurrentAdminSession" -> true
                "createClass" -> { created = true; mapOf("id" to "class-1", "name" to "Math") }
                else -> error("Unexpected academic-store call: $method")
            }
        }
        val useCase = createClass(store)

        val result = useCase.createClass(CreateClassDto(actor("admin"), "Math", "MATH-1", 2026, "Manhã", "create-class-123"))

        assertEquals(mapOf("id" to "class-1", "name" to "Math"), result)
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
    fun `published academic page validates bounds and snapshots collections`() {
        val sourceRow = mutableMapOf<String, Any?>("id" to "item-1")
        val sourceItems = mutableListOf<Map<String, Any?>>(sourceRow)
        val page = AcademicPage(sourceItems, 1, 20, 1)

        sourceRow["id"] = "changed"
        sourceItems.clear()
        assertEquals("item-1", page.items.single()["id"])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (page.items as MutableList<Map<String, Any?>>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (page.items.single() as MutableMap<String, Any?>)["id"] = "changed"
        }
        assertThrows(IllegalArgumentException::class.java) { AcademicPage(emptyList(), 0, 20, 0) }
        assertThrows(IllegalArgumentException::class.java) { AcademicPage(emptyList(), 1, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { AcademicPage(emptyList(), 1, 20, -1) }
    }

    @Test
    fun `published academic page snapshots nested projection collections`() {
        val studentIds = mutableListOf("student-1")
        val teacher = mutableMapOf<String, Any?>("id" to "teacher-1", "name" to "Teacher")
        val teachers = mutableListOf<Map<String, Any?>>(teacher)
        val publicationIds = mutableListOf("publication-1")
        val unassignedAssessmentIds = mutableListOf("assessment-1")
        val enrollment = mutableMapOf<String, Any?>("id" to "enrollment-1", "unassignedAssessmentIds" to unassignedAssessmentIds)
        val enrollments = mutableListOf<Map<String, Any?>>(enrollment)
        val sourceRow = mutableMapOf<String, Any?>(
            "studentIds" to studentIds,
            "teachers" to teachers,
            "publicationIds" to publicationIds,
            "enrollments" to enrollments,
        )
        val page = AcademicPage(listOf(sourceRow), 1, 20, 1)

        studentIds += "student-2"
        teacher["name"] = "Changed"
        teachers.clear()
        publicationIds.clear()
        unassignedAssessmentIds += "assessment-2"
        enrollment["changed"] = true
        enrollments.clear()
        sourceRow.clear()

        val snapshot = page.items.single()
        assertEquals(listOf("student-1"), snapshot["studentIds"])
        assertEquals(listOf(mapOf("id" to "teacher-1", "name" to "Teacher")), snapshot["teachers"])
        assertEquals(listOf("publication-1"), snapshot["publicationIds"])
        assertEquals(listOf(mapOf("id" to "enrollment-1", "unassignedAssessmentIds" to listOf("assessment-1"))), snapshot["enrollments"])

        assertThrows(UnsupportedOperationException::class.java) { (snapshot["studentIds"] as MutableList<Any?>).add("student-3") }
        assertThrows(UnsupportedOperationException::class.java) { (snapshot["teachers"] as MutableList<Any?>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { ((snapshot["teachers"] as List<Map<String, Any?>>).single() as MutableMap<String, Any?>).put("name", "Changed") }
        assertThrows(UnsupportedOperationException::class.java) { (snapshot["publicationIds"] as MutableList<Any?>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (snapshot["enrollments"] as MutableList<Any?>).clear() }
        val enrollmentSnapshot = (snapshot["enrollments"] as List<Map<String, Any?>>).single()
        assertThrows(UnsupportedOperationException::class.java) { (enrollmentSnapshot as MutableMap<String, Any?>).put("changed", true) }
        assertThrows(UnsupportedOperationException::class.java) { (enrollmentSnapshot["unassignedAssessmentIds"] as MutableList<Any?>).clear() }
    }

    private fun createClass(store: AcademicStoreOutputPort): CreateClassUseCase = CreateClassUseCase(
        store,
        proxy { method, _ -> if (method == "serialize") "class-request".toByteArray() else error("Unexpected serializer call: $method") },
        proxy<IdempotencyStoreOutputPort> { method, _ -> when (method) { "find" -> null; "save" -> null; else -> error("Unexpected idempotency call: $method") } },
        object : TransactionRunnerOutputPort {
            override fun <T> run(request: TransactionRunDto<T>): T = request.block()
        },
    )

    private fun actor(role: String) = AuthenticatedActor("user-1", role, true, Instant.EPOCH, "session-reference")

    private inline fun <reified T> proxy(crossinline answer: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            if (method.declaringClass == Any::class.java) return@newProxyInstance method.name
            answer(method.name, args ?: emptyArray())
        } as T
}
