package br.com.ajadigital.core.materials.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.materials.domain.ports.`in`.DiscardFileUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore

class DiscardFile(private val store: ContentStore) : DiscardFileUseCase {
    override fun discard(actor: AcademicActor, id: String) =
        store.discard(MaterialsSupport.id(id), actor.id, actor.role, actor.sessionTokenHash)
}
