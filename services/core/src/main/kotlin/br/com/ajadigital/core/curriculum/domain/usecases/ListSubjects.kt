package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListSubjectsUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore

class ListSubjects(
    private val store: SubjectStore,
) : ListSubjectsUseCase {
    override fun subjects(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, query: String?): AcademicPage {
        CurriculumSupport.requireReader(actor)
        return store.subjects(
            actor.id,
            actor.role,
            actor.sessionTokenHash,
            CurriculumSupport.checkedPage(page),
            CurriculumSupport.checkedSize(size),
            CurriculumSupport.checkedSort(sort),
            classId,
            query?.trim(),
        )
    }
}
