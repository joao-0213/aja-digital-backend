package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Conflict
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.NotFound
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Forbidden
import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.PublishAssessmentCommand
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishAssessmentInputPort
import br.com.ajadigital.core.assessment.application.models.GetAssessmentQuery
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentWriteOutputPort
import br.com.ajadigital.core.assessment.application.models.AssessmentReceiptKey

class PublishAssessmentUseCase(private val store: AssessmentWriteOutputPort, private val read: AssessmentReadOutputPort) : PublishAssessmentInputPort {
    override fun publish(request: PublishAssessmentCommand): AssessmentDetails.Staff {
        val (actor, id, key, expectedRevision, hash) = request
        AssessmentPolicy.writer(actor.role, actor.active)
        AssessmentPolicy.id(id)
        require(expectedRevision > 0)
        AssessmentPolicy.validateCommand(key, hash)
        val query = GetAssessmentQuery(actor, id)
        when (read.assessment(query)) {
            null -> throw NotFound()
            is AssessmentDetails.Student -> throw Forbidden()
            is AssessmentDetails.Staff -> Unit
        }
        store.replay(AssessmentReceiptKey.publish(actor.id, id, key, hash))?.let { return it }
        return store.publish(request)
            ?: throw Conflict(read.assessment(query)?.record?.revision)
    }
}
