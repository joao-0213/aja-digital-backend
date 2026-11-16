package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.models.MaterialCreate
import br.com.ajadigital.core.materials.domain.ports.`in`.PublishMaterialsUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class PublishMaterials(
    private val store: ContentStore,
    private val idempotency: IdempotencyStore,
    private val mapper: ObjectMapper,
    private val transactionRunner: TransactionRunner,
) : PublishMaterialsUseCase {
    override fun publish(actor: AcademicActor, key: String, moduleId: String, revision: Int, items: List<MaterialCreate>): Map<String, Any?> =
        transactionRunner.run {
            MaterialsSupport.writer(actor)
            require(key.length in 8..128 && MaterialsSupport.validId(moduleId) && revision > 0 && items.size in 1..10)
            require(items.all { it.moduleId == moduleId && MaterialsSupport.validId(it.moduleId) && MaterialsSupport.validMaterial(it) })
            store.authorizeMaterialPublication(actor.id, actor.role, actor.sessionTokenHash, moduleId)
            val body = mapOf("moduleId" to moduleId, "expectedRevision" to revision, "items" to items)
            MaterialsSupport.idempotent(idempotency, mapper, actor, "publishMaterials", "/materials/batch", key, body) {
                val result = store.publishMaterials(actor.id, actor.role, actor.sessionTokenHash, moduleId, revision, items) ?: throw AcademicConflict()
                MaterialsSupport.page(result)
            }
        }
}
