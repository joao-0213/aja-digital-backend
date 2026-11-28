package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization

import br.com.ajadigital.core.academic.application.models.AcademicStoreEnrollmentPreviewDto

import br.com.ajadigital.core.academic.application.models.PreviewEnrollmentDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.PreviewEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.identity.application.contracts.EmailAddress

class PreviewEnrollmentUseCase(private val store: AcademicStoreOutputPort) : PreviewEnrollmentInputPort {
    override fun preview(request: PreviewEnrollmentDto): AcademicEnrollmentPreview {
        val (actor, classId, email) = request
                AcademicAuthorization.requireAdmin(store, actor)
        val value = email.trim()
        require(EmailAddress.isValid(value))
        return store.enrollmentPreview(AcademicStoreEnrollmentPreviewDto(classId, value)) ?: throw AcademicNotFound()
    }
}
