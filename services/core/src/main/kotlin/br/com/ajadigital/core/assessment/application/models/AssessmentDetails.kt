package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.AssessmentRecord
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionView
import java.util.Collections

sealed interface AssessmentDetails {
    val record: AssessmentRecord

    class Staff(
        override val record: AssessmentRecord,
        questions: List<AssessmentQuestionView>,
        val hasSubmissions: Boolean,
    ) : AssessmentDetails {
        val questions: List<AssessmentQuestionView> = Collections.unmodifiableList(questions.toList())
    }

    data class Student(
        override val record: AssessmentRecord,
        val questionCount: Int,
        val assigned: Boolean,
    ) : AssessmentDetails

}
