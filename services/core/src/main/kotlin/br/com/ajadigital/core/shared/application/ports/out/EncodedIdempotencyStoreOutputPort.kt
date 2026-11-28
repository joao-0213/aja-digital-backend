package br.com.ajadigital.core.shared.application.ports.out

import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto

interface EncodedIdempotencyStoreOutputPort {
    fun findEncoded(request: FindIdempotencyDto): String?

    fun saveEncoded(
        request: FindIdempotencyDto,
        response: String,
    )
}
