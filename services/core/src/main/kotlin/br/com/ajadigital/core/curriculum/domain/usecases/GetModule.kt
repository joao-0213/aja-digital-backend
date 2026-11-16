package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.GetModuleUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore

class GetModule(
    private val store: SubjectStore,
) : GetModuleUseCase {
    override fun module(actor: AcademicActor, id: String): Map<String, Any?> {
        CurriculumSupport.requireReader(actor)
        return store.module(id, actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()
    }
}
