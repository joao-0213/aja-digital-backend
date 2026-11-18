package br.com.ajadigital.core.materials.application.ports.`in`

import br.com.ajadigital.core.materials.application.models.DiscardFileCommand

fun interface DiscardFileInputPort {
    fun discard(request: DiscardFileCommand): Unit
}
