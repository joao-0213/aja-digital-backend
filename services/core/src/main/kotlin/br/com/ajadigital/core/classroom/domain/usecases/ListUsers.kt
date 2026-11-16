package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListUsersUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class ListUsers(private val store: AcademicStore) : ListUsersUseCase {
    override fun users(actor: AcademicActor, page: Int, size: Int, sort: String, role: String?, active: Boolean?, q: String?): AcademicPage {
        ClassroomSupport.requireAdmin(store, actor)
        return store.listUsers(
            ClassroomSupport.checkedPage(page),
            ClassroomSupport.checkedSize(size),
            ClassroomSupport.checkedSort(sort),
            role?.also { require(it in ClassroomSupport.roles) },
            active,
            q?.trim(),
        )
    }
}
