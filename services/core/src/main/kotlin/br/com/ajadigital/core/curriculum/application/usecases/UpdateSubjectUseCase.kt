package br.com.ajadigital.core.curriculum.application.usecases
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy

import br.com.ajadigital.core.curriculum.domain.models.*

import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.ports.`in`.UpdateSubjectInputPort
import br.com.ajadigital.core.curriculum.application.models.UpdateSubjectDto
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class UpdateSubjectUseCase(
    private val store: SubjectStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : UpdateSubjectInputPort {
    override fun updateSubject(request: UpdateSubjectDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, revision, classId, name, teacherIds, color, icon, description) = request
                CurriculumPolicy.requireAdmin(actor)
        require(revision > 0 && (name == null || name.isNotBlank() && name.length <= 100) && (description == null || description.length <= 10000) && (color == null || color in CurriculumPolicy.colors) && (icon == null || icon in CurriculumPolicy.icons))
        require(teacherIds == null || teacherIds.size <= 100 && teacherIds.distinct().size == teacherIds.size)
        CurriculumPolicy.subjectCommand(store.updateSubject(UpdateSubjectRecordDto(id, actor.id, actor.sessionValidationReference, revision, classId, name?.trim(), teacherIds, color, icon, description)) ?: CurriculumPolicy.conflict(store.subject(GetStoredSubjectDto(id, actor.id, "admin", actor.sessionValidationReference))))
    })
}
