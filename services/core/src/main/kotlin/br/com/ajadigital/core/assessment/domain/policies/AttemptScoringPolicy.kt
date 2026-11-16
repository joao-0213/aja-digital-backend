package br.com.ajadigital.core.assessment.domain.policies

import java.math.BigDecimal
import java.math.RoundingMode

object AttemptScoringPolicy {
    data class ScoreResult(val score: BigDecimal, val maxScore: BigDecimal)

    fun areAnswersValid(answers: Map<String, Int?>, questionOptionCounts: Map<String, Int>): Boolean =
        answers.keys.all(questionOptionCounts::containsKey) &&
            answers.none { (questionId, value) -> value != null && value !in 0 until questionOptionCounts.getValue(questionId) }

    fun calculateScore(
        questions: List<Pair<String, Int?>>,
        answers: Map<String, Int?>,
        maxGrade: BigDecimal?,
    ): ScoreResult {
        require(questions.isNotEmpty())
        val correct = questions.count { (id, correctIndex) -> answers[id] == correctIndex }
        val maxScore = maxGrade ?: BigDecimal.valueOf(questions.size.toLong())
        val score = maxScore
            .multiply(BigDecimal.valueOf(correct.toLong()))
            .divide(BigDecimal.valueOf(questions.size.toLong()), 2, RoundingMode.HALF_UP)
        return ScoreResult(score = score, maxScore = maxScore)
    }
}
