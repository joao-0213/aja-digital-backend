package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.models.ListModulesQuery
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.store.StoreModulesQuery
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListModulesInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.shared.application.contracts.Page

class ListModulesUseCase(
    private val store: SubjectStoreOutputPort,
) : ListModulesInputPort {
    override fun modules(request: ListModulesQuery): Page<Module> {
        val (actor, page, size, sort, subjectId) = request
        CurriculumPolicy.requireReader(actor.active, actor.role, actor.emailConfirmedAt != null)
        return store.modules(StoreModulesQuery(
            actor.id, actor.role,
            CurriculumPolicy.checkedPage(page), CurriculumPolicy.checkedSize(size),
            CurriculumPolicy.checkedModuleSort(sort), subjectId,
        ))
    }
}
