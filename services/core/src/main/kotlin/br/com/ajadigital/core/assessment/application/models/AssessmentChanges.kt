package br.com.ajadigital.core.assessment.application.models

import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentKind
import br.com.ajadigital.core.shared.application.contracts.FieldChange
import java.time.Instant

data class AssessmentChanges(
    val moduleId: FieldChange<String> = FieldChange.Unchanged,
    val title: FieldChange<String> = FieldChange.Unchanged,
    val description: FieldChange<String> = FieldChange.Unchanged,
    val kind: FieldChange<AssessmentKind> = FieldChange.Unchanged,
    val maxGrade: FieldChange<Double?> = FieldChange.Unchanged,
    val deadline: FieldChange<Instant?> = FieldChange.Unchanged,
    val required: FieldChange<Boolean> = FieldChange.Unchanged,
    val allowLate: FieldChange<Boolean> = FieldChange.Unchanged,
) {
    fun applyTo(current: AssessmentDraft) = AssessmentDraft(
        moduleId.resolve(current.moduleId),
        title.resolve(current.title),
        description.resolve(current.description),
        kind.resolve(current.kind),
        maxGrade.resolve(current.maxGrade),
        deadline.resolve(current.deadline),
        required.resolve(current.required),
        allowLate.resolve(current.allowLate),
    )

    private fun <T> FieldChange<T>.resolve(prior: T): T = when (this) {
        FieldChange.Unchanged -> prior
        is FieldChange.Set -> value
    }
}
