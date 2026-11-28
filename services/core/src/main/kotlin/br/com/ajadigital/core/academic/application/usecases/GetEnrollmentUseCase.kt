package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindEnrollmentDto
import br.com.ajadigital.core.academic.application.models.GetEnrollmentDto
import br.com.ajadigital.core.academic.application.ports.`in`.GetEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class GetEnrollmentUseCase(private val store: AcademicStoreOutputPort) : GetEnrollmentInputPort {
    override fun enrollment(request: GetEnrollmentDto): AcademicEnrollmentView {
        val (actor, id) = request
        AcademicAuthorization.requireAdmin(store, actor)
        return store.findEnrollment(AcademicStoreFindEnrollmentDto(id)) ?: throw AcademicNotFound()
    }
}
