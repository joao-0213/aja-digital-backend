package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.DeactivateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class DeactivateUser(
    private val store: AcademicStore,
    private val transactionRunner: TransactionRunner,
) : DeactivateUserUseCase {
    override fun deactivate(actor: AcademicActor, id: String, revision: Int): Map<String, Any?> =
        transactionRunner.run {
            store.deactivateUser(id, ClassroomSupport.requireAdmin(store, actor, true).id, revision.also { require(it > 0) })
                ?: ClassroomSupport.conflict(store.findUser(id))
        }
}
