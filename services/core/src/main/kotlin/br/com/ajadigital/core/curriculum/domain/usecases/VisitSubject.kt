package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.VisitSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class VisitSubject(
    private val store: SubjectStore,
    private val transactionRunner: TransactionRunner,
) : VisitSubjectUseCase {
    override fun visit(actor: AcademicActor, subjectId: String, publicationIds: List<String>?): Map<String, Any?> {
        val execute = {
            CurriculumSupport.requireReader(actor)
            require(publicationIds == null || publicationIds.distinct().size == publicationIds.size && publicationIds.all { it.length <= 139 && it.matches(Regex("^(material|notice|assessment):[A-Za-z0-9][A-Za-z0-9._:-]*$")) })
            store.visit(subjectId, actor.id, actor.role, actor.sessionTokenHash, publicationIds) ?: throw AcademicNotFound()
        }
        return if (publicationIds != null) transactionRunner.run(execute) else execute()
    }
}
