package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Conflict
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Forbidden
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.NotFound
import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentCommand
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateAssessmentInputPort
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentRecord
import br.com.ajadigital.core.assessment.domain.policies.AssessmentEditPolicy
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentWriteOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.shared.application.contracts.FieldChange
import br.com.ajadigital.core.shared.application.contracts.valueOrNull
import br.com.ajadigital.core.assessment.application.models.AssessmentReceiptKey

class UpdateAssessmentUseCase(
    private val store: AssessmentWriteOutputPort,
    private val read: AssessmentReadOutputPort,
    snapshots: QuestionSnapshotProviderOutputPort,
) : UpdateAssessmentInputPort {
    private val prepare = PrepareAssessmentQuestions(snapshots)

    override fun update(request: UpdateAssessmentCommand): AssessmentDetails.Staff {
        val (actor, id, key, hash, expectedRevision, changes, selection, confirmation) = request
        AssessmentPolicy.writer(actor.role, actor.active)
        AssessmentPolicy.id(id)
        AssessmentPolicy.validateCommand(key, hash)
        require(expectedRevision > 0)
        val current = when (val found = read.assessment(GetAssessmentQuery(actor, id))) {
            null -> throw NotFound()
            is AssessmentDetails.Student -> throw Forbidden()
            is AssessmentDetails.Staff -> found
        }
        store.replay(AssessmentReceiptKey.update(actor.id, id, key, hash))?.let { return it }

        val edit = requireNotNull(changes)
        edit.moduleId.valueOrNull()?.let(AssessmentPolicy::id)
        edit.title.valueOrNull()?.let(AssessmentPolicy::validateTitle)
        edit.description.valueOrNull()?.let(AssessmentPolicy::validateDescription)
        if (edit.maxGrade is FieldChange.Set) AssessmentPolicy.validateMaxGrade(edit.maxGrade.value)
        val prior = current.record.draft
        val draft = edit.applyTo(prior)
        store.authorizeWrite(actor, prior.moduleId)
        if (draft.moduleId != prior.moduleId) {
            store.authorizeWrite(actor, draft.moduleId)
        }
        val questions = when (selection) {
            FieldChange.Unchanged -> null
            is FieldChange.Set -> {
                val selected = requireNotNull(selection.value)
                require(AssessmentEditPolicy.isValidKindQuestionCount(draft.kind, selected.items.size))
                prepare.identify(selected, current.questions)
            }
        }
        val revision = current.record.revision
        if (!AssessmentEditPolicy.isValidKindQuestionCount(draft.kind, questions?.size ?: current.questions.size)) {
            throw Conflict(revision)
        }
        val reset = requireNotNull(confirmation)
        if (questions != null && !reset && AssessmentEditPolicy.questionsChanged(current.questions, questions)) {
            throw Conflict(revision)
        }
        val prepared = questions?.let { prepare.capture(it, current.questions.mapTo(mutableSetOf()) { question -> question.id }) }
        return store.update(UpdateAssessmentRecord(actor, id, key, hash, expectedRevision, draft, prepared, reset))
            ?: throw Conflict(revision)
    }
}
