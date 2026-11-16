package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.UpdateSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class UpdateSubject(
    private val store: SubjectStore,
    private val transactionRunner: TransactionRunner,
) : UpdateSubjectUseCase {
    override fun updateSubject(actor: AcademicActor, id: String, revision: Int, classId: String?, name: String?, teacherIds: List<String>?, color: String?, icon: String?, description: String?): Map<String, Any?> = transactionRunner.run {
        CurriculumSupport.requireAdmin(actor)
        require(revision > 0 && (name == null || name.isNotBlank() && name.length <= 100) && (description == null || description.length <= 10000) && (color == null || color in CurriculumSupport.colors) && (icon == null || icon in CurriculumSupport.icons))
        require(teacherIds == null || teacherIds.size <= 100 && teacherIds.distinct().size == teacherIds.size)
        CurriculumSupport.subjectCommand(store.updateSubject(id, actor.id, actor.sessionTokenHash, revision, classId, name?.trim(), teacherIds, color, icon, description) ?: CurriculumSupport.conflict(store.subject(id, actor.id, "admin", actor.sessionTokenHash)))
    }
}
