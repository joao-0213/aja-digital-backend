package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CreateSubjectUseCase {
    fun createSubject(actor: AcademicActor, classId: String, name: String, teacherIds: List<String>, color: String, icon: String, description: String, key: String): Map<String, Any?>
}
