package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class GetClass(private val store: AcademicStore) : GetClassUseCase {
    override fun classById(actor: AcademicActor, id: String): Map<String, Any?> {
        ClassroomSupport.requireReader(actor)
        return store.findClass(id, actor.id, actor.role) ?: throw AcademicNotFound()
    }
}
