package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.contracts.ListSubjectsQuery
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary
import br.com.ajadigital.core.curriculum.application.models.store.StoreListSubjectsQuery
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectsInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.shared.application.contracts.Page

class ListSubjectsUseCase(
    private val store: SubjectStoreOutputPort,
) : ListSubjectsInputPort {
    override fun subjects(request: ListSubjectsQuery): Page<SubjectSummary> {
        val actor = request.actor
        val page = request.page
        val size = request.size
        val sort = request.sort
        val classId = request.classId
        val query = request.query
        CurriculumPolicy.requireReader(actor.active, actor.role, actor.emailConfirmedAt != null)
        return store.subjects(
            StoreListSubjectsQuery(
                actor.id,
                actor.role,
                CurriculumPolicy.checkedPage(page),
                CurriculumPolicy.checkedSize(size),
                CurriculumPolicy.checkedSort(sort),
                classId,
                query?.trim(),
            ),
        )
    }
}
