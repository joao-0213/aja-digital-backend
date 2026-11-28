package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.ReorderModulesCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreReorderModulesCommand
import br.com.ajadigital.core.curriculum.application.models.valueOrThrow
import br.com.ajadigital.core.curriculum.application.ports.`in`.ReorderModulesInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.shared.application.contracts.Page
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class ReorderModulesUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : ReorderModulesInputPort {
    override fun reorder(request: ReorderModulesCommand): Page<Module> = transactionRunner.run {
        val actor = request.actor
        CurriculumPolicy.requireWriterRole(actor.active, actor.role)
        CurriculumPolicy.validateOrder(request.revision, request.items)
        store
            .reorderModules(
                StoreReorderModulesCommand(
                    request.subjectId,
                    actor.id,
                    actor.role,
                    actor.sessionValidationReference,
                    request.revision,
                    request.items,
                    CurriculumPolicy.checkedPage(request.page),
                    CurriculumPolicy.checkedSize(request.pageSize),
                ),
            ).valueOrThrow()
    }
}
