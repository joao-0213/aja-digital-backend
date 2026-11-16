package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.GetSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore

class GetSubject(
    private val store: SubjectStore,
) : GetSubjectUseCase {
    override fun subject(actor: AcademicActor, id: String): Map<String, Any?> {
        CurriculumSupport.requireReader(actor)
        return store.subject(id, actor.id, actor.role, actor.sessionTokenHash) ?: throw AcademicNotFound()
    }
}
