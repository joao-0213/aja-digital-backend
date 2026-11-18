package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.models.ListSubjectModulesQuery
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectModulesQuery
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectModulesInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

class ListSubjectModulesUseCase(
    private val store: SubjectStoreOutputPort,
) : ListSubjectModulesInputPort {
    override fun subjectModules(request: ListSubjectModulesQuery): List<Module> {
        val (actor, subjectId) = request
        CurriculumPolicy.requireReader(actor.active, actor.role, actor.emailConfirmedAt != null)
        return store.subjectModules(StoreSubjectModulesQuery(subjectId, actor.id, actor.role))
    }
}
