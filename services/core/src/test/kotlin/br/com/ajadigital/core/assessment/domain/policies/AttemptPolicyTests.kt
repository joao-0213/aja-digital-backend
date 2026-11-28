package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentForbidden
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AttemptPolicyTests {
    @Test
    fun `student mutations require active confirmed student`() {
        assertDoesNotThrow { AttemptPolicy.student("student", true, true) }
        assertThrows(AssessmentForbidden::class.java) { AttemptPolicy.student("student", false, true) }
        assertThrows(AssessmentForbidden::class.java) { AttemptPolicy.student("student", true, false) }
        assertThrows(AssessmentForbidden::class.java) { AttemptPolicy.student("teacher", true, true) }
    }

    @Test
    fun `readers allow active staff and require confirmation for students`() {
        listOf("admin", "teacher").forEach { role ->
            assertDoesNotThrow { AttemptPolicy.reader(role, true, false) }
        }
        assertDoesNotThrow { AttemptPolicy.reader("student", true, true) }
        assertThrows(AssessmentForbidden::class.java) { AttemptPolicy.reader("student", true, false) }
        assertThrows(AssessmentForbidden::class.java) { AttemptPolicy.reader("other", true, true) }
        assertThrows(AssessmentForbidden::class.java) { AttemptPolicy.reader("teacher", false, true) }
    }
}
