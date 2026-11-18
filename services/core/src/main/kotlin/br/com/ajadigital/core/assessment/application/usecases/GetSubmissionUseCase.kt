package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.SubmissionStoreSubmissionDto

import br.com.ajadigital.core.assessment.application.models.GetSubmissionDto

import br.com.ajadigital.core.assessment.domain.policies.SubmissionPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.GetSubmissionInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class GetSubmissionUseCase(private val store: SubmissionStoreOutputPort) : GetSubmissionInputPort {
    override fun submission(request: GetSubmissionDto): Map<String, Any?> {
                val (actor, id) = request
        SubmissionPolicy.reader(actor)
        SubmissionPolicy.id(id)
        return store.submission(SubmissionStoreSubmissionDto(actor.id, actor.role, actor.sessionValidationReference, id)) ?: throw AcademicNotFound()
    }
}
