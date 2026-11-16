package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.WithdrawUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class WithdrawUser(
    private val store: AcademicStore,
    private val transactionRunner: TransactionRunner,
) : WithdrawUserUseCase {
    override fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?> =
        transactionRunner.run {
            store.withdrawUser(id, ClassroomSupport.requireAdmin(store, actor, true).id, revision.also { require(it > 0) })
                ?: ClassroomSupport.conflict(store.findUser(id))
        }
}
