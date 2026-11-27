package br.com.ajadigital.core.academic.application.usecases
import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.capabilities.AcademicAuthorization

import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto

import br.com.ajadigital.core.academic.application.models.AcademicStoreUpdateClassDto

import br.com.ajadigital.core.academic.application.models.UpdateClassDto

import br.com.ajadigital.core.academic.domain.policies.AcademicPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateClassInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class UpdateClassUseCase(
    private val store: AcademicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateClassInputPort {
    override fun updateClass(request: UpdateClassDto): AcademicClassView {
                val (actor, id, revision, name, code, year, shift) = request
        return         transactionRunner.run(TransactionRunDto {
            store.updateClass(AcademicStoreUpdateClassDto(id, AcademicAuthorization.requireAdmin(store, actor, true).id, revision.also { require(it > 0) }, name?.trim(), code?.trim(), year, shift))
                ?: AcademicPolicy.conflict(store.findClass(AcademicStoreFindClassDto(id, actor.id, "admin"))?.revision)
        })
    }
}
