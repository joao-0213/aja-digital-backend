package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.ports.`in`.ReorderModulesInputPort
import br.com.ajadigital.core.curriculum.application.models.ReorderModulesDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class ReorderModulesUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReorderModulesInputPort {
    override fun reorder(request: ReorderModulesDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, subjectId, revision, items, page, pageSize) = request
                CurriculumPolicy.requireWriterRole(actor)
        require(revision > 0 && items.isNotEmpty() && items.map { it.first }.distinct().size == items.size && items.all { it.second > 0 })
        val result = store.reorderModules(ReorderModulesRecordDto(subjectId, actor.id, actor.role, actor.sessionValidationReference, revision, items, CurriculumPolicy.checkedPage(page), CurriculumPolicy.checkedSize(pageSize))) ?: CurriculumPolicy.conflict(store.subject(GetStoredSubjectDto(subjectId, actor.id, actor.role, actor.sessionValidationReference)))
        mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    })
}
