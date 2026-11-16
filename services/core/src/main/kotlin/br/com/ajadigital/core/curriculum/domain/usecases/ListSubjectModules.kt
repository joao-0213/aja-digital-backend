package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListSubjectModulesUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore

class ListSubjectModules(
    private val store: SubjectStore,
) : ListSubjectModulesUseCase {
    override fun subjectModules(actor: AcademicActor, subjectId: String): List<Map<String, Any?>> {
        CurriculumSupport.requireReader(actor)
        return store.subjectModules(subjectId, actor.id, actor.role, actor.sessionTokenHash)
    }
}
