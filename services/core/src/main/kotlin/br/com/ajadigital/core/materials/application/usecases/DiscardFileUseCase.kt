package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.DiscardFileCommand
import br.com.ajadigital.core.materials.application.ports.`in`.DiscardFileInputPort
import br.com.ajadigital.core.materials.application.ports.out.FileStoreOutputPort
import br.com.ajadigital.core.materials.domain.policies.MaterialRules

class DiscardFileUseCase(
    private val store: FileStoreOutputPort,
) : DiscardFileInputPort {
    override fun discard(request: DiscardFileCommand) {
        MaterialRules.id(request.id)
        request.actor.requireSessionRole()
        store.discard(request)
    }
}
