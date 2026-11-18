package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.models.GetModuleQuery
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.store.StoreModuleQuery
import br.com.ajadigital.core.curriculum.application.ports.`in`.GetModuleInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumNotFound
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

class GetModuleUseCase(
    private val store: SubjectStoreOutputPort,
) : GetModuleInputPort {
    override fun module(request: GetModuleQuery): Module {
        val (actor, id) = request
        CurriculumPolicy.requireReader(actor.active, actor.role, actor.emailConfirmedAt != null)
        return store.module(StoreModuleQuery(id, actor.id, actor.role)) ?: throw CurriculumNotFound()
    }
}
