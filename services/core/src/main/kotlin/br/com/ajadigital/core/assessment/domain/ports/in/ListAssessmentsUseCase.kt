package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ListAssessmentsUseCase {
    fun assessments(actor: AcademicActor, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?): Map<String, Any?>
}
