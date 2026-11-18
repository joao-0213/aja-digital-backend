package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.capabilities.AcademicIdempotency

import br.com.ajadigital.core.academic.application.models.AcademicStoreFindEnrollmentDto

import br.com.ajadigital.core.academic.application.models.AcademicStoreTransitionEnrollmentDto

import br.com.ajadigital.core.academic.application.models.TransitionEnrollmentDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.TransitionEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class TransitionEnrollmentUseCase(
    private val store: AcademicStoreOutputPort,
    private val objectMapper: JsonSerializerOutputPort,
    private val idempotency: IdempotencyStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : TransitionEnrollmentInputPort {
    override fun transitionEnrollment(request: TransitionEnrollmentDto): Map<String, Any?> {
                val (actor, id, revision, command, key) = request
        return         transactionRunner.run(TransactionRunDto {
            AcademicIdempotency.execute(idempotency, objectMapper, AcademicAuthorization.requireAdmin(store, actor, true), "${command}Enrollment", "/enrollments/$id/$command", key, mapOf("expectedRevision" to revision)) {
                store.transitionEnrollment(AcademicStoreTransitionEnrollmentDto(id, actor.id, revision.also { require(it > 0) }, command)) ?: AcademicPolicy.conflict(store.findEnrollment(AcademicStoreFindEnrollmentDto(id)))
            }
        })
    }
}
