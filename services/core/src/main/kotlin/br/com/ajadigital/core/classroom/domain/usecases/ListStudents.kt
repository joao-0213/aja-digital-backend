package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListStudentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class ListStudents(private val store: AcademicStore) : ListStudentsUseCase {
    override fun students(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, q: String?, history: Boolean): AcademicPage {
        ClassroomSupport.requireStaffReader(actor)
        return store.listStudents(actor.id, actor.role, ClassroomSupport.checkedPage(page), ClassroomSupport.checkedSize(size), ClassroomSupport.checkedSort(sort), classId, q?.trim(), history)
    }
}
