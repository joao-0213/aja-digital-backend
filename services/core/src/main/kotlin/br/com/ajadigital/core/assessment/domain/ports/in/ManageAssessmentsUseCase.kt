package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface ManageAssessmentsUseCase {
    fun create(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?>
    fun update(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?>
    fun publish(actor: AcademicActor, id: String, key: String, expectedRevision: Int, body: JsonNode): Map<String, Any?>
    fun assessment(actor: AcademicActor, id: String): Map<String, Any?>
    fun assessments(actor: AcademicActor, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?): Map<String, Any?>
    fun dashboardAssessments(actor: AcademicActor, pageSize: Int = 100): Map<String, Any?>
    fun pendingStudentCount(actor: AcademicActor): Int
}
