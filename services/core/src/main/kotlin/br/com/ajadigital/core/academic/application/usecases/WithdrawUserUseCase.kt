package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization

import br.com.ajadigital.core.academic.application.models.AcademicStoreWithdrawUserDto

import br.com.ajadigital.core.academic.application.models.WithdrawUserDto

import br.com.ajadigital.core.academic.application.models.FindUserDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.WithdrawUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class WithdrawUserUseCase(
    private val store: AcademicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : WithdrawUserInputPort {
    override fun withdraw(request: WithdrawUserDto): AcademicUserView {
        val (actor, id, revision) = request
                return         transactionRunner.run(TransactionRunDto {
            store.withdrawUser(AcademicStoreWithdrawUserDto(id, AcademicAuthorization.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }))
                ?: AcademicPolicy.conflict(store.findUser(FindUserDto(id))?.revision)
        })
    }
}
