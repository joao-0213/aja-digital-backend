package br.com.ajadigital.questions

import br.com.ajadigital.questions.domain.question.Question
import br.com.ajadigital.questions.domain.question.toSnapshot
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class QuestionSnapshotTests {
    @Test
    fun `content hash distinguishes option boundaries`() {
        val question = Question(
            "q1", 1, Instant.parse("2025-01-01T12:00:00Z"), "Math", "Algebra", "easy", "AJA", 1,
            "Resolva.", listOf("A"), 0, "1", null,
        )
        val differentComposition = question.copy(options = listOf("A", "0"), correctOption = 1, explanation = "")

        assertNotEquals(question.toSnapshot().contentHash, differentComposition.toSnapshot().contentHash)
    }
}
