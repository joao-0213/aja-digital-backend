package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.PreviewEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.identity.domain.policies.EmailAddress

class PreviewEnrollment(private val store: AcademicStore) : PreviewEnrollmentUseCase {
    override fun preview(actor: AcademicActor, classId: String, email: String): Map<String, Any?> {
        ClassroomSupport.requireAdmin(store, actor)
        val value = email.trim()
        require(EmailAddress.isValid(value))
        return store.enrollmentPreview(classId, value) ?: throw AcademicNotFound()
    }
}
