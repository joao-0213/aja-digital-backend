package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListMyEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class ListMyEnrollments(private val store: AcademicStore) : ListMyEnrollmentsUseCase {
    override fun myEnrollments(actor: AcademicActor, page: Int, size: Int, sort: String): AcademicPage {
        if (actor.role != "student" || !actor.active || actor.emailConfirmedAt == null) throw AcademicForbidden()
        return store.myEnrollments(actor.id, ClassroomSupport.checkedPage(page), ClassroomSupport.checkedSize(size), ClassroomSupport.checkedSort(sort))
    }
}
