package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.ReopenClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class ReopenClass(
    private val store: AcademicStore,
    private val objectMapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : ReopenClassUseCase {
    override fun reopenClass(actor: AcademicActor, id: String, revision: Int, key: String): Map<String, Any?> =
        transactionRunner.run {
            ClassroomSupport.idempotent(idempotency, objectMapper, ClassroomSupport.requireAdmin(store, actor, true), "reopenClass", "/classes/$id/reopen", key, mapOf("expectedRevision" to revision)) {
                store.setClassActive(id, actor.id, revision, true) ?: ClassroomSupport.conflict(store.findClass(id, actor.id, "admin"))
            }
        }
}
