package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentCommand
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateAssessmentInputPort
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentRecord
import br.com.ajadigital.core.assessment.domain.policies.AssessmentEditPolicy
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentWriteOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.assessment.application.models.AssessmentReceiptKey

class CreateAssessmentUseCase(
    private val store: AssessmentWriteOutputPort,
    snapshots: QuestionSnapshotProviderOutputPort,
) : CreateAssessmentInputPort {
    private val prepare = PrepareAssessmentQuestions(snapshots)

    override fun create(request: CreateAssessmentCommand): AssessmentDetails.Staff {
        val (actor, key, hash, draft, selection) = request
        AssessmentPolicy.writer(actor.role, actor.active)
        AssessmentPolicy.validateCommand(key, hash)
        AssessmentPolicy.validateDraft(draft)
        require(AssessmentEditPolicy.isValidKindQuestionCount(draft.kind, selection.items.size))
        val questions = prepare.identify(selection, emptyList())
        store.authorizeWrite(actor, draft.moduleId)
        store.replay(AssessmentReceiptKey.create(actor.id, key, hash))?.let { return it }
        val captured = prepare.capture(questions, emptySet())
        return store.create(CreateAssessmentRecord(actor, key, hash, draft, captured))
    }
}
