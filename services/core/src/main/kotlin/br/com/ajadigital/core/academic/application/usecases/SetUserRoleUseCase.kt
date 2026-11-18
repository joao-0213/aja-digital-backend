package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization

import br.com.ajadigital.core.academic.application.models.AcademicStoreChangeRoleDto

import br.com.ajadigital.core.academic.application.models.SetUserRoleDto

import br.com.ajadigital.core.academic.application.models.FindUserDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.SetUserRoleInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class SetUserRoleUseCase(
    private val store: AcademicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : SetUserRoleInputPort {
    override fun setRole(request: SetUserRoleDto): Map<String, Any?> {
        val (actor, id, revision, role) = request
                return         transactionRunner.run(TransactionRunDto {
            store.changeRole(AcademicStoreChangeRoleDto(id, AcademicAuthorization.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }, role.also { require(it in AcademicPolicy.roles) }))
                ?: AcademicPolicy.conflict(store.findUser(FindUserDto(id)))
        })
    }
}
