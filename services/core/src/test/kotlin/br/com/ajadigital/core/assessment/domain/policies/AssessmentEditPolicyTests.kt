package br.com.ajadigital.core.assessment.domain.policies

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AssessmentEditPolicyTests {
    @Test
    fun `validates kind and question count`() {
        assertTrue(AssessmentEditPolicy.isValidKindQuestionCount("work", 0))
        assertFalse(AssessmentEditPolicy.isValidKindQuestionCount("work", 1))
        assertTrue(AssessmentEditPolicy.isValidKindQuestionCount("quiz", 1))
        assertFalse(AssessmentEditPolicy.isValidKindQuestionCount("quiz", 0))
    }

    @Test
    fun `allows publish only for draft without submissions and valid question count`() {
        assertTrue(AssessmentEditPolicy.canPublish("draft", hasSubmissions = false, kind = "quiz", questionCount = 2))
        assertTrue(AssessmentEditPolicy.canPublish("draft", hasSubmissions = false, kind = "work", questionCount = 0))
        assertFalse(AssessmentEditPolicy.canPublish("published", hasSubmissions = false, kind = "quiz", questionCount = 2))
        assertFalse(AssessmentEditPolicy.canPublish("draft", hasSubmissions = true, kind = "quiz", questionCount = 2))
        assertFalse(AssessmentEditPolicy.canPublish("draft", hasSubmissions = false, kind = "quiz", questionCount = 0))
        assertFalse(AssessmentEditPolicy.canPublish("draft", hasSubmissions = false, kind = "work", questionCount = 1))
    }

    @Test
    fun `rejects structural changes after submission`() {
        val result = AssessmentEditPolicy.resolvePostSubmissionDeadline(
            sameStructure = false,
            oldDeadline = null,
            requestedDeadline = null,
        )
        assertFalse(result.allowed)
    }

    @Test
    fun `preserves Sao Paulo local time when moving deadline date after submission`() {
        val oldDeadline = Instant.parse("2026-11-11T01:00:00Z") // 2026-11-10 22:00 in America/Sao_Paulo (-03:00)
        val requestedDeadline = Instant.parse("2026-11-16T01:00:00Z") // 2026-11-15 22:00 in America/Sao_Paulo (-03:00)
        val result = AssessmentEditPolicy.resolvePostSubmissionDeadline(
            sameStructure = true,
            oldDeadline = oldDeadline,
            requestedDeadline = requestedDeadline,
        )
        assertTrue(result.allowed)
        assertEquals(Instant.parse("2026-11-16T01:00:00Z"), result.resolvedDeadline)
    }

    @Test
    fun `rejects deadline time-of-day or nullness change after submission`() {
        val oldDeadline = Instant.parse("2026-11-10T15:30:00Z")
        val differentTime = Instant.parse("2026-11-15T16:30:00Z")
        assertFalse(AssessmentEditPolicy.resolvePostSubmissionDeadline(true, oldDeadline, differentTime).allowed)
        assertFalse(AssessmentEditPolicy.resolvePostSubmissionDeadline(true, oldDeadline, null).allowed)
        assertFalse(AssessmentEditPolicy.resolvePostSubmissionDeadline(true, null, differentTime).allowed)
        val bothNull = AssessmentEditPolicy.resolvePostSubmissionDeadline(true, null, null)
        assertTrue(bothNull.allowed)
        assertNull(bothNull.resolvedDeadline)
    }
}
