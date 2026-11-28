package br.com.ajadigital.core.assessment.application.ports.out

import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.application.models.AssessmentReceiptKey
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentRecord
import br.com.ajadigital.core.assessment.application.models.PublishAssessmentCommand
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentRecord
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface AssessmentWriteOutputPort {
    fun authorizeWrite(
        actor: AuthenticatedActor,
        moduleId: String,
    )

    fun replay(request: AssessmentReceiptKey): AssessmentDetails.Staff?

    fun create(request: CreateAssessmentRecord): AssessmentDetails.Staff

    fun update(request: UpdateAssessmentRecord): AssessmentDetails.Staff?

    fun publish(request: PublishAssessmentCommand): AssessmentDetails.Staff?
}
