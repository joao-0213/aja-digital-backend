package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class ListClasses(private val store: AcademicStore) : ListClassesUseCase {
    override fun classes(actor: AcademicActor, page: Int, size: Int, sort: String, active: Boolean?, q: String?): AcademicPage {
        ClassroomSupport.requireReader(actor)
        return store.listClasses(actor.id, actor.role, ClassroomSupport.checkedPage(page), ClassroomSupport.checkedSize(size), ClassroomSupport.checkedSort(sort), active, q?.trim())
    }
}
