package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class UpdateUser(
    private val store: AcademicStore,
    private val transactionRunner: TransactionRunner,
) : UpdateUserUseCase {
    override fun updateUser(actor: AcademicActor, id: String, revision: Int, name: String?, email: String?): Map<String, Any?> =
        transactionRunner.run {
            store.updateUser(id, ClassroomSupport.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }, name?.trim(), email?.trim())
                ?: ClassroomSupport.conflict(store.findUser(id))
        }
}
