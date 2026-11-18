package br.com.ajadigital.core.shared.domain.ports.out

import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

interface JsonSerializerOutputPort {
    fun serialize(request: SerializeJsonDto): ByteArray
}
