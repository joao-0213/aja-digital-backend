package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class GetUser(private val store: AcademicStore) : GetUserUseCase {
    override fun user(actor: AcademicActor, id: String): Map<String, Any?> {
        ClassroomSupport.requireAdmin(store, actor)
        return store.findUser(id) ?: throw AcademicNotFound()
    }
}
