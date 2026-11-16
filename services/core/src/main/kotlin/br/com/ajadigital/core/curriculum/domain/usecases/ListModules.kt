package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListModulesUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore

class ListModules(
    private val store: SubjectStore,
) : ListModulesUseCase {
    override fun modules(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?): AcademicPage {
        CurriculumSupport.requireReader(actor)
        return store.modules(
            actor.id,
            actor.role,
            actor.sessionTokenHash,
            CurriculumSupport.checkedPage(page),
            CurriculumSupport.checkedSize(size),
            CurriculumSupport.checkedModuleSort(sort),
            subjectId,
        )
    }
}
