package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class AttemptPolicyTests {
    @Test
    fun `student mutations require active confirmed student`() {
        assertDoesNotThrow { AttemptPolicy.student(actor("student", active = true, confirmed = true)) }
        assertThrows(AcademicForbidden::class.java) { AttemptPolicy.student(actor("student", active = false, confirmed = true)) }
        assertThrows(AcademicForbidden::class.java) { AttemptPolicy.student(actor("student", active = true, confirmed = false)) }
        assertThrows(AcademicForbidden::class.java) { AttemptPolicy.student(actor("teacher", active = true, confirmed = true)) }
    }

    @Test
    fun `readers allow active staff and require confirmation for students`() {
        listOf("admin", "teacher").forEach { role ->
            assertDoesNotThrow { AttemptPolicy.reader(actor(role, active = true, confirmed = false)) }
        }
        assertDoesNotThrow { AttemptPolicy.reader(actor("student", active = true, confirmed = true)) }
        assertThrows(AcademicForbidden::class.java) { AttemptPolicy.reader(actor("student", active = true, confirmed = false)) }
        assertThrows(AcademicForbidden::class.java) { AttemptPolicy.reader(actor("other", active = true, confirmed = true)) }
        assertThrows(AcademicForbidden::class.java) { AttemptPolicy.reader(actor("teacher", active = false, confirmed = true)) }
    }

    private fun actor(role: String, active: Boolean, confirmed: Boolean) = AuthenticatedActor(
        id = "actor-1",
        role = role,
        active = active,
        emailConfirmedAt = Instant.EPOCH.takeIf { confirmed },
        sessionValidationReference = "session-reference",
    )
}
