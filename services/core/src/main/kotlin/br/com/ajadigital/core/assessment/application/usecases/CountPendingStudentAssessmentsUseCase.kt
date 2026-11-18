package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AssessmentStorePendingStudentCountDto

import br.com.ajadigital.core.assessment.application.models.CountPendingStudentAssessmentsDto

import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingStudentAssessmentsInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class CountPendingStudentAssessmentsUseCase(private val store: AssessmentStoreOutputPort) : CountPendingStudentAssessmentsInputPort {
    override fun pendingStudentCount(request: CountPendingStudentAssessmentsDto): Int {
                val (actor) = request
        AssessmentPolicy.reader(actor)
        require(actor.role == "student") { throw AcademicForbidden() }
        return store.pendingStudentCount(AssessmentStorePendingStudentCountDto(actor.id, actor.sessionValidationReference))
    }
}
