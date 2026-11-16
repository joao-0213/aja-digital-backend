package br.com.ajadigital.core.assessment.domain.policies

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AttemptScoringPolicyTests {
    @Test
    fun `validates answers against question option bounds`() {
        val optionCounts = mapOf("q1" to 3, "q2" to 2)
        assertTrue(AttemptScoringPolicy.areAnswersValid(mapOf("q1" to 0, "q2" to null), optionCounts))
        assertTrue(AttemptScoringPolicy.areAnswersValid(mapOf("q1" to 2), optionCounts))
        assertFalse(AttemptScoringPolicy.areAnswersValid(mapOf("q3" to 0), optionCounts))
        assertFalse(AttemptScoringPolicy.areAnswersValid(mapOf("q1" to 3), optionCounts))
        assertFalse(AttemptScoringPolicy.areAnswersValid(mapOf("q1" to -1), optionCounts))
    }

    @Test
    fun `calculates score with default maxScore equal to question count`() {
        val questions = listOf("q1" to 1, "q2" to 0, "q3" to 2)
        val answers = mapOf("q1" to 1, "q2" to 1, "q3" to 2)
        val result = AttemptScoringPolicy.calculateScore(questions, answers, maxGrade = null)
        assertEquals(BigDecimal("3"), result.maxScore)
        assertEquals(BigDecimal("2.00"), result.score)
    }

    @Test
    fun `calculates score scaled to maxGrade with HALF_UP rounding and guards empty questions`() {
        val questions = listOf("q1" to 1, "q2" to 0)
        val tieResult = AttemptScoringPolicy.calculateScore(questions, mapOf("q1" to 1), maxGrade = BigDecimal("5.05"))
        assertEquals(BigDecimal("5.05"), tieResult.maxScore)
        assertEquals(BigDecimal("2.53"), tieResult.score) // 2.525 -> HALF_UP 2.53 (HALF_EVEN would give 2.52)

        val zeroResult = AttemptScoringPolicy.calculateScore(questions, emptyMap(), maxGrade = BigDecimal("10"))
        assertEquals(BigDecimal("0.00"), zeroResult.score)

        assertThrows(IllegalArgumentException::class.java) {
            AttemptScoringPolicy.calculateScore(emptyList(), emptyMap(), maxGrade = null)
        }
    }
}
