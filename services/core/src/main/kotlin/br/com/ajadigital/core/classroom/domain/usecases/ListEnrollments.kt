package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class ListEnrollments(private val store: AcademicStore) : ListEnrollmentsUseCase {
    override fun enrollments(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, studentId: String?, status: List<String>?, q: String?): AcademicPage {
        ClassroomSupport.requireReader(actor)
        return store.listEnrollments(
            actor.id,
            actor.role,
            ClassroomSupport.checkedPage(page),
            ClassroomSupport.checkedSize(size),
            ClassroomSupport.checkedSort(sort),
            classId,
            studentId,
            status?.also { require(it.isNotEmpty() && it.size <= 4 && it.distinct().size == it.size && it.all { s -> s in ClassroomSupport.statuses }) },
            q?.trim(),
        )
    }
}
