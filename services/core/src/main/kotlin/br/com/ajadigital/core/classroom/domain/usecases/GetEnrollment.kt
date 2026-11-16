package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class GetEnrollment(private val store: AcademicStore) : GetEnrollmentUseCase {
    override fun enrollment(actor: AcademicActor, id: String): Map<String, Any?> {
        ClassroomSupport.requireAdmin(store, actor)
        return store.findEnrollment(id) ?: throw AcademicNotFound()
    }
}
