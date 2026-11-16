package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UpdateSubjectUseCase {
    fun updateSubject(actor: AcademicActor, id: String, revision: Int, classId: String?, name: String?, teacherIds: List<String>?, color: String?, icon: String?, description: String?): Map<String, Any?>
}
