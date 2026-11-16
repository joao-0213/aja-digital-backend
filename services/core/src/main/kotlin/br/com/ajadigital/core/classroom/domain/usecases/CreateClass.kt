package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class CreateClass(
    private val store: AcademicStore,
    private val objectMapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : CreateClassUseCase {
    override fun createClass(actor: AcademicActor, name: String, code: String, year: Int, shift: String, key: String): Map<String, Any?> =
        transactionRunner.run {
            val admin = ClassroomSupport.requireAdmin(store, actor, true)
            require(name.isNotBlank() && code.isNotBlank() && year in 1900..2200 && shift in ClassroomSupport.shifts)
            ClassroomSupport.idempotent(idempotency, objectMapper, admin, "createClass", "/classes", key, mapOf("name" to name, "code" to code, "year" to year, "shift" to shift)) {
                store.createClass(admin.id, name.trim(), code.trim(), year, shift)
            }
        }
}
