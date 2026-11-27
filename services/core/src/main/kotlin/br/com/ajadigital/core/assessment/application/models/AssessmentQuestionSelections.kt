package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionContent
import java.util.Collections

class AssessmentQuestionSelections(items: List<Selection>) {
    val items: List<Selection> = Collections.unmodifiableList(items.toList())

    sealed interface Selection {
        data class Bank(val questionId: String) : Selection
        data class Authored(
            val clientKey: String,
            val questionId: String?,
            val content: AssessmentQuestionContent,
        ) : Selection
    }
}
