package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.UpdateModuleUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class UpdateModule(
    private val store: SubjectStore,
    private val transactionRunner: TransactionRunner,
) : UpdateModuleUseCase {
    override fun updateModule(actor: AcademicActor, id: String, revision: Int, title: String?, description: String?): Map<String, Any?> = transactionRunner.run {
        CurriculumSupport.requireWriterRole(actor)
        require(revision > 0 && (title == null || title.isNotBlank() && title.length <= 160) && (description == null || description.length <= 10000))
        store.updateModule(id, actor.id, actor.role, actor.sessionTokenHash, revision, title?.trim(), description) ?: CurriculumSupport.conflict(store.module(id, actor.id, actor.role, actor.sessionTokenHash))
    }
}
