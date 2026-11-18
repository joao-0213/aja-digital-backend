package br.com.ajadigital.core.curriculum.application.usecases

import br.com.ajadigital.core.curriculum.application.models.SubjectVisit
import br.com.ajadigital.core.curriculum.application.models.RecordSubjectVisitCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreVisitSubjectCommand
import br.com.ajadigital.core.curriculum.application.ports.`in`.VisitSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumNotFound
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class VisitSubjectUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : VisitSubjectInputPort {
    override fun readVisit(actor: AuthenticatedActor, subjectId: String): SubjectVisit {
        CurriculumPolicy.requireReader(actor.active, actor.role, actor.emailConfirmedAt != null)
        return visit(actor, subjectId, null)
    }

    override fun recordVisit(command: RecordSubjectVisitCommand): SubjectVisit = transactionRunner.run {
        val actor = command.actor
        CurriculumPolicy.requireReader(actor.active, actor.role, actor.emailConfirmedAt != null)
        CurriculumPolicy.validateVisit(command.publicationIds)
        visit(actor, command.subjectId, command.publicationIds)
    }

    private fun visit(actor: AuthenticatedActor, subjectId: String, publicationIds: List<String>?): SubjectVisit =
        store.visit(StoreVisitSubjectCommand(subjectId, actor.id, actor.role, actor.sessionValidationReference, publicationIds))
            ?: throw CurriculumNotFound()
}
