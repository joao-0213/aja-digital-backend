package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.contracts.MaterialKind
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.MaterialDraft
import br.com.ajadigital.core.materials.application.models.PublishMaterialsCommand
import br.com.ajadigital.core.materials.application.models.StorePublishMaterialsCommand
import br.com.ajadigital.core.materials.application.ports.`in`.PublishMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialPublicationReceiptOutputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsIdempotencyConflict
import br.com.ajadigital.core.materials.domain.models.NewMaterial
import br.com.ajadigital.core.materials.domain.policies.MaterialRules
import br.com.ajadigital.core.shared.application.contracts.Page
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class PublishMaterialsUseCase(
    private val store: MaterialWriteOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val codec: MaterialPublicationReceiptOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : PublishMaterialsInputPort {
    override fun publish(request: PublishMaterialsCommand): Page<Material> = transactionRunner.run {
        val actor = request.actor
        actor.requireWriter()
        require(request.key.length in 8..128 && MaterialRules.validId(request.moduleId) && request.revision > 0 && request.items.size in 1..10)
        val items = request.items.map {
            require(it.moduleId == request.moduleId)
            it.toNewMaterial()
        }
        val command = StorePublishMaterialsCommand(actor, request.moduleId, request.revision, items)
        store.authorizeMaterialPublication(actor, request.moduleId)
        val key = FindIdempotencyDto(actor.id, "publishMaterials", "/materials/batch", request.key, codec.fingerprint(command))
        val receipt = try {
            idempotency.findEncoded(key)
        } catch (_: IdempotencyKeyReuse) {
            throw MaterialsIdempotencyConflict()
        }
        if (receipt != null) {
            codec.decode(receipt)
        } else {
            val published = store.publishMaterials(command)
            idempotency.saveEncoded(key, codec.encode(published))
            published
        }
    }

    private fun MaterialDraft.toNewMaterial(): NewMaterial {
        val source = when (requireNotNull(kind)) {
            MaterialKind.LESSON -> {
                require(fileId == null && videoUrl == null)
                NewMaterial.Source.Lesson
            }
            MaterialKind.PDF -> {
                require(videoUrl == null)
                NewMaterial.Source.Pdf(requireNotNull(fileId))
            }
            MaterialKind.VIDEO -> {
                require(fileId == null)
                NewMaterial.Source.Video(requireNotNull(videoUrl))
            }
        }
        return NewMaterial(moduleId, title, duration, content, source)
    }
}
