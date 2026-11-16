package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.TransitionEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class TransitionEnrollment(
    private val store: AcademicStore,
    private val objectMapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : TransitionEnrollmentUseCase {
    override fun transitionEnrollment(actor: AcademicActor, id: String, revision: Int, command: String, key: String): Map<String, Any?> =
        transactionRunner.run {
            ClassroomSupport.idempotent(idempotency, objectMapper, ClassroomSupport.requireAdmin(store, actor, true), "${command}Enrollment", "/enrollments/$id/$command", key, mapOf("expectedRevision" to revision)) {
                store.transitionEnrollment(id, actor.id, revision.also { require(it > 0) }, command) ?: ClassroomSupport.conflict(store.findEnrollment(id))
            }
        }
}
