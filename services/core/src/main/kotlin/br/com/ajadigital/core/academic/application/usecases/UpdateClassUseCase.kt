package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreUpdateClassDto
import br.com.ajadigital.core.academic.application.models.UpdateClassDto
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class UpdateClassUseCase(
    private val store: AcademicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateClassInputPort {
    override fun updateClass(request: UpdateClassDto): AcademicClassView {
        val actor = request.actor
        val id = request.id
        val revision = request.revision
        val name = request.name
        val code = request.code
        val year = request.year
        val shift = request.shift
        return transactionRunner.run {
            store.updateClass(AcademicStoreUpdateClassDto(id, AcademicAuthorization.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }, name?.trim(), code?.trim(), year, shift))
                ?: AcademicPolicy.conflict(store.findClass(AcademicStoreFindClassDto(id, actor.id, "admin"))?.revision)
        }
    }
}
