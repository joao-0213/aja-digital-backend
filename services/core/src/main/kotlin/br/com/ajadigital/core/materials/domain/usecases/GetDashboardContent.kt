package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.models.DashboardContent
import br.com.ajadigital.core.materials.domain.ports.`in`.GetDashboardContentUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class GetDashboardContent(private val store: ContentStore) : GetDashboardContentUseCase {
    override fun dashboard(actor: AcademicActor): DashboardContent {
        MaterialsSupport.reader(actor)
        require(actor.role == "student") { throw AcademicForbidden() }
        return store.dashboard(actor.id, actor.role, actor.sessionTokenHash, 100)
    }
}
