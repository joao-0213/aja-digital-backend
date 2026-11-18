package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary
import br.com.ajadigital.core.curriculum.application.models.GetSubjectQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectQuery
import br.com.ajadigital.core.curriculum.application.ports.`in`.GetSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumNotFound
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

class GetSubjectUseCase(
    private val store: SubjectStoreOutputPort,
) : GetSubjectInputPort {
    override fun subject(request: GetSubjectQuery): SubjectSummary {
        val (actor, id) = request
        CurriculumPolicy.requireReader(actor.active, actor.role, actor.emailConfirmedAt != null)
        return store.subject(StoreSubjectQuery(id, actor.id, actor.role)) ?: throw CurriculumNotFound()
    }
}
