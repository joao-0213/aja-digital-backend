package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization

import br.com.ajadigital.core.academic.application.models.AcademicStoreDeactivateUserDto

import br.com.ajadigital.core.academic.application.models.DeactivateUserDto

import br.com.ajadigital.core.academic.application.models.FindUserDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.DeactivateUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class DeactivateUserUseCase(
    private val store: AcademicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : DeactivateUserInputPort {
    override fun deactivate(request: DeactivateUserDto): AcademicUserView {
        val (actor, id, revision) = request
                return transactionRunner.run {
            store.deactivateUser(AcademicStoreDeactivateUserDto(id, AcademicAuthorization.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }))
                ?: AcademicPolicy.conflict(store.findUser(FindUserDto(id))?.revision)
        }
    }
}
