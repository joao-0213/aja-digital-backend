package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.models.CreateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.store.StoreAuthorizeSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateModuleCommand
import br.com.ajadigital.core.curriculum.application.ports.`in`.CreateModuleInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.CurriculumReceiptCodecOutputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto

class CreateModuleUseCase(
    private val store: SubjectStoreOutputPort,
    private val codec: CurriculumReceiptCodecOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateModuleInputPort {
    override fun createModule(request: CreateModuleCommand): Module = transactionRunner.run {
        val actor = request.actor
        CurriculumPolicy.requireWriterRole(actor.active, actor.role)
        store.authorize(StoreAuthorizeSubjectCommand(actor.id, actor.role, actor.sessionValidationReference, request.subjectId))
        require(request.key.length in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH)
        val receipt = FindIdempotencyDto(actor.id, "createModule", "/modules", request.key, codec.fingerprint(request))
        idempotency.receipt(receipt)?.let { return@run codec.decodeModule(it) }
        CurriculumPolicy.validateModuleFields(request.title, request.description)
        val result =
            store.createModule(
                StoreCreateModuleCommand(
                    actor.id,
                    actor.role,
                    actor.sessionValidationReference,
                    request.subjectId,
                    request.title.trim(),
                    request.description,
                ),
            )
        idempotency.saveEncoded(receipt, codec.encode(result))
        result
    }
}

private const val MIN_IDEMPOTENCY_KEY_LENGTH = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128
