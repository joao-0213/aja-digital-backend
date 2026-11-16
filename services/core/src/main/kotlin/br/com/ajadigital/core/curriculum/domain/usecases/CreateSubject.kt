package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.CreateSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class CreateSubject(
    private val store: SubjectStore,
    private val mapper: ObjectMapper,
    private val idempotency: IdempotencyStore,
    private val transactionRunner: TransactionRunner,
) : CreateSubjectUseCase {
    override fun createSubject(actor: AcademicActor, classId: String, name: String, teacherIds: List<String>, color: String, icon: String, description: String, key: String): Map<String, Any?> = transactionRunner.run {
        CurriculumSupport.requireAdmin(actor)
        store.authorize(actor.id, actor.role, actor.sessionTokenHash)
        CurriculumSupport.idempotent(idempotency, mapper, actor, "createSubject", "/subjects", key, mapOf("classId" to classId, "name" to name, "teacherIds" to teacherIds, "color" to color, "icon" to icon, "description" to description)) {
            require(name.isNotBlank() && name.length <= 100 && description.length <= 10000 && color in CurriculumSupport.colors && icon in CurriculumSupport.icons)
            require(teacherIds.size <= 100 && teacherIds.distinct().size == teacherIds.size)
            CurriculumSupport.subjectCommand(store.createSubject(actor.id, actor.sessionTokenHash, classId, name.trim(), teacherIds, color, icon, description))
        }
    }
}
