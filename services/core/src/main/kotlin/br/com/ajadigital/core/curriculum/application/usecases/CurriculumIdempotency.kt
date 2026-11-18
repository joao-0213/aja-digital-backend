package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumIdempotencyConflict
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto


internal fun EncodedIdempotencyStoreOutputPort.receipt(request: FindIdempotencyDto): String? = try {
    findEncoded(request)
} catch (_: IdempotencyKeyReuse) {
    throw CurriculumIdempotencyConflict()
}
