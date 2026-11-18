package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.ports.`in`.VisitSubjectInputPort
import br.com.ajadigital.core.curriculum.application.models.VisitSubjectDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

class VisitSubjectUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : VisitSubjectInputPort {
    override fun visit(request: VisitSubjectDto): Map<String, Any?> {
        val (actor, subjectId, publicationIds) = request
                val execute = {
            CurriculumPolicy.requireReader(actor)
            require(publicationIds == null || publicationIds.distinct().size == publicationIds.size && publicationIds.all { it.length <= 139 && it.matches(Regex("^(material|notice|assessment):[A-Za-z0-9][A-Za-z0-9._:-]*$")) })
            store.visit(VisitSubjectRecordDto(subjectId, actor.id, actor.role, actor.sessionValidationReference, publicationIds)) ?: throw AcademicNotFound()
        }
        return if (publicationIds != null) transactionRunner.run(TransactionRunDto(execute)) else execute()
    }
}
