package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListDashboardClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class ListDashboardClasses(private val store: AcademicStore) : ListDashboardClassesUseCase {
    override fun dashboardClasses(actor: AcademicActor, pageSize: Int): AcademicPage {
        ClassroomSupport.requireReader(actor)
        return store.dashboardClasses(actor.id, actor.role, pageSize)
    }
}
