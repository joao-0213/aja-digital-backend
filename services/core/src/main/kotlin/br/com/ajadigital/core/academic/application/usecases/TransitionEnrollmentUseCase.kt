package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindEnrollmentDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreTransitionEnrollmentDto
import br.com.ajadigital.core.academic.application.models.TransitionEnrollmentDto
import br.com.ajadigital.core.academic.application.ports.`in`.TransitionEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class TransitionEnrollmentUseCase(
    private val store: AcademicStoreOutputPort,
    private val fingerprints: AcademicRequestFingerprintOutputPort,
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val receiptCodec: AcademicReceiptCodecOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : TransitionEnrollmentInputPort {
    override fun transitionEnrollment(request: TransitionEnrollmentDto): AcademicEnrollmentView {
        val actor = request.actor
        val id = request.id
        val revision = request.revision
        val command = request.command
        val key = request.key
        return transactionRunner.run {
            AcademicIdempotency.execute(
                idempotency,
                AcademicAuthorization.requireAdmin(store, actor, true),
                "${command}Enrollment",
                "/enrollments/$id/$command",
                key,
                { fingerprints.expectedRevision(revision) },
                encode = { receiptCodec.encode(it) },
                decode = receiptCodec::decodeEnrollment,
            ) {
                store.transitionEnrollment(AcademicStoreTransitionEnrollmentDto(id, actor.id, revision.also { require(it > 0) }, command)) ?: AcademicPolicy.conflict(store.findEnrollment(AcademicStoreFindEnrollmentDto(id))?.revision)
            }
        }
    }
}
