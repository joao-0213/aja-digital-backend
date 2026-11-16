package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.SetUserRoleUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class SetUserRole(
    private val store: AcademicStore,
    private val transactionRunner: TransactionRunner,
) : SetUserRoleUseCase {
    override fun setRole(actor: AcademicActor, id: String, revision: Int, role: String): Map<String, Any?> =
        transactionRunner.run {
            store.changeRole(id, ClassroomSupport.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }, role.also { require(it in ClassroomSupport.roles) })
                ?: ClassroomSupport.conflict(store.findUser(id))
        }
}
