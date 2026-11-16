package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class UpdateClass(
    private val store: AcademicStore,
    private val transactionRunner: TransactionRunner,
) : UpdateClassUseCase {
    override fun updateClass(actor: AcademicActor, id: String, revision: Int, name: String?, code: String?, year: Int?, shift: String?): Map<String, Any?> =
        transactionRunner.run {
            store.updateClass(id, ClassroomSupport.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }, name?.trim(), code?.trim(), year, shift)
                ?: ClassroomSupport.conflict(store.findClass(id, actor.id, "admin"))
        }
}
