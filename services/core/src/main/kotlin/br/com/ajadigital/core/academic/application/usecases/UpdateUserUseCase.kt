package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.models.AcademicStoreUpdateUserDto
import br.com.ajadigital.core.academic.application.models.AcademicUserView
import br.com.ajadigital.core.academic.application.models.FindUserDto
import br.com.ajadigital.core.academic.application.models.UpdateUserDto
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class UpdateUserUseCase(
    private val store: AcademicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateUserInputPort {
    override fun updateUser(request: UpdateUserDto): AcademicUserView {
        val actor = request.actor
        val id = request.id
        val revision = request.revision
        val name = request.name
        val email = request.email
        return transactionRunner.run {
            store.updateUser(AcademicStoreUpdateUserDto(id, AcademicAuthorization.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }, name?.trim(), email?.trim()))
                ?: AcademicPolicy.conflict(store.findUser(FindUserDto(id))?.revision)
        }
    }
}
