package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ListSubmissionsUseCase {
    fun submissions(actor: AcademicActor, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): Map<String, Any?>
}
