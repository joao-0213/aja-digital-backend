package br.com.ajadigital.core.materials.application.usecases
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.application.models.ContentStoreAuthorizeMaterialPublicationDto

import br.com.ajadigital.core.materials.application.models.ContentStorePublishMaterialsDto

import br.com.ajadigital.core.materials.application.models.PublishMaterialsDto

import br.com.ajadigital.core.materials.domain.policies.MaterialsPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.materials.domain.models.MaterialCreate
import br.com.ajadigital.core.materials.application.ports.`in`.PublishMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class PublishMaterialsUseCase(
    private val store: ContentStoreOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val mapper: JsonSerializerOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : PublishMaterialsInputPort {
    override fun publish(request: PublishMaterialsDto): Map<String, Any?> = run { val (actor, key, moduleId, revision, items) = request; transactionRunner.run(TransactionRunDto {
            MaterialsPolicy.writer(actor)
            require(key.length in 8..128 && MaterialsPolicy.validId(moduleId) && revision > 0 && items.size in 1..10)
            require(items.all { it.moduleId == moduleId && MaterialsPolicy.validId(it.moduleId) && MaterialsPolicy.validMaterial(it) })
            store.authorizeMaterialPublication(ContentStoreAuthorizeMaterialPublicationDto(actor.id, actor.role, actor.sessionValidationReference, moduleId))
            val body = mapOf("moduleId" to moduleId, "expectedRevision" to revision, "items" to items)
            MaterialsPolicy.idempotent(idempotency, mapper, actor, "publishMaterials", "/materials/batch", key, body) {
                val result = store.publishMaterials(ContentStorePublishMaterialsDto(actor.id, actor.role, actor.sessionValidationReference, moduleId, revision, items)) ?: throw AcademicConflict()
                MaterialsPolicy.page(result)
            }
        }) }
}
