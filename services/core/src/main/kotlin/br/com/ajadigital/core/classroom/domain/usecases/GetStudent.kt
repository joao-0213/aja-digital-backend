package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetStudentUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class GetStudent(private val store: AcademicStore) : GetStudentUseCase {
    override fun student(actor: AcademicActor, id: String): Map<String, Any?> {
        ClassroomSupport.requireStaffReader(actor)
        return store.findStudent(id, actor.id, actor.role) ?: throw AcademicNotFound()
    }
}
