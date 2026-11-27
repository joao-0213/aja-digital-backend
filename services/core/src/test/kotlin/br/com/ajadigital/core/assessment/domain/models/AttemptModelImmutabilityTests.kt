package br.com.ajadigital.core.assessment.domain.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import br.com.ajadigital.core.assessment.application.models.AttemptAnswerSaveCommand

class AttemptModelImmutabilityTests {
    @Test
    fun `attempt records take ordered immutable snapshots of answers and nested question data`() {
        val answers = linkedMapOf("q2" to 1, "q1" to null)
        val order = mutableListOf("q2", "q1")
        val fields = mutableListOf(AttemptQuestionField.ID, AttemptQuestionField.YEAR)
        val options = mutableListOf("a", "b")
        val prompts = mutableListOf(AttemptPrompt("q1", fields, 2026, null, null, options, null, null, null, null))
        val attempt = AttemptRecord(
            "attempt-1", 1, "2026-11-27T12:00:00Z", "2026-11-27T12:00:00Z", null, "assessment-1", "Quiz", "student-1", null,
            "2026-11-27T12:00:00Z", AttemptState.InProgress, answers, order, prompts, null,
        )

        answers["q2"] = 0
        order.reverse()
        fields.clear()
        options[0] = "changed"
        prompts.clear()

        assertEquals(listOf("q2", "q1"), attempt.answers.keys.toList())
        assertEquals(1, attempt.answers["q2"])
        assertEquals(listOf("q2", "q1"), attempt.questionOrder)
        assertEquals(1, attempt.questions.size)
        assertEquals(listOf(AttemptQuestionField.ID, AttemptQuestionField.YEAR), attempt.questions.single().fields)
        assertEquals(listOf("a", "b"), attempt.questions.single().options)
        assertThrows(UnsupportedOperationException::class.java) { (attempt.answers as MutableMap)["q3"] = 0 }
        assertThrows(UnsupportedOperationException::class.java) { (attempt.questionOrder as MutableList).clear() }
    }

    @Test
    fun `question results pages and answer commands copy mutable collections`() {
        val fields = mutableListOf(AttemptQuestionField.ID, AttemptQuestionField.IMAGE)
        val imageFields = mutableListOf(AttemptQuestionImageField.ALT, AttemptQuestionImageField.SRC)
        val options = mutableListOf("a", "b")
        val questions = mutableListOf(AttemptQuestion("q1", fields, null, AttemptQuestionImage(imageFields, "alt", "src"), null, 0, options, null, null, null, null, null))
        val record = AttemptRecord(
            "attempt-1", 2, "created", "updated", null, "assessment-1", "Quiz", "student-1", null, "started",
            AttemptState.Submitted(java.time.Instant.EPOCH), emptyMap(), listOf("q1"), emptyList(), null,
        )
        val result = AttemptResult(record, AttemptReceipt("receipt-1", "attempt-1", "submitted"), 1.0, 1.0, questions)
        val records = mutableListOf(record)
        val page = AttemptPage(records, 1, 20, 1)
        val answerValues = linkedMapOf("q1" to 0)
        val command = AttemptAnswerSaveCommand("user-1", "student", "session", "attempt-1", 1, answerValues)

        fields.clear()
        imageFields.clear()
        options.clear()
        questions.clear()
        records.clear()
        answerValues.clear()

        assertEquals(1, result.questions.size)
        assertEquals(listOf(AttemptQuestionField.ID, AttemptQuestionField.IMAGE), result.questions.single().fields)
        assertEquals(listOf("a", "b"), result.questions.single().options)
        assertEquals(listOf(AttemptQuestionImageField.ALT, AttemptQuestionImageField.SRC), result.questions.single().image?.fields)
        assertEquals(1, page.items.size)
        assertEquals(listOf("q1"), command.answers.keys.toList())
        assertThrows(UnsupportedOperationException::class.java) { (command.answers as MutableMap)["q2"] = 1 }
    }
}
