package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.CreateModuleUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class CreateModule(
    private val store: SubjectStore,
    private val mapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : CreateModuleUseCase {
    override fun createModule(actor: AcademicActor, subjectId: String, title: String, description: String, key: String): Map<String, Any?> =
        transactionRunner.run {
            CurriculumSupport.requireWriterRole(actor)
            store.authorize(actor.id, actor.role, actor.sessionTokenHash, subjectId)
            CurriculumSupport.idempotent(idempotency, mapper, actor, "createModule", "/modules", key, mapOf("subjectId" to subjectId, "title" to title, "description" to description)) {
                require(title.isNotBlank() && title.length <= 160 && description.length <= 10000)
                store.createModule(actor.id, actor.role, actor.sessionTokenHash, subjectId, title.trim(), description)
            }
        }
}
