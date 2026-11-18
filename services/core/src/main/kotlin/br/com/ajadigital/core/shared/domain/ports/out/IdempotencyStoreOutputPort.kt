package br.com.ajadigital.core.shared.domain.ports.out

import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto


interface IdempotencyStoreOutputPort {
    fun find(request: FindIdempotencyDto): Map<String, Any?>?
    fun save(request: SaveIdempotencyDto)
}
