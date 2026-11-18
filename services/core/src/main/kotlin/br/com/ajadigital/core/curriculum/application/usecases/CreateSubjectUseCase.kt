package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.models.CreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreAuthorizeSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.ports.`in`.CreateSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.CurriculumReceiptCodecOutputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort


class CreateSubjectUseCase(
    private val store: SubjectStoreOutputPort,
    private val codec: CurriculumReceiptCodecOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : CreateSubjectInputPort {
    override fun createSubject(request: CreateSubjectCommand): Subject =
        transactionRunner.run {
            val actor = request.actor
            CurriculumPolicy.requireAdmin(actor.active, actor.role)
            store.authorize(StoreAuthorizeSubjectCommand(actor.id, actor.role, actor.sessionValidationReference, null))
            require(request.key.length in 8..128)
            val receipt = FindIdempotencyDto(actor.id, "createSubject", "/subjects", request.key, codec.fingerprint(request))
            idempotency.receipt(receipt)?.let { return@run codec.decodeSubject(it) }
            CurriculumPolicy.validateSubjectFields(request.name, request.teacherIds, request.color, request.icon, request.description)
            val result = store.createSubject(StoreCreateSubjectCommand(
                actor.id, actor.sessionValidationReference, request.classId, request.name.trim(),
                request.teacherIds, request.color, request.icon, request.description,
            ))
            idempotency.saveEncoded(receipt, codec.encode(result))
            result
        }
}
