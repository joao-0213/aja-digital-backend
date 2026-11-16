package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface ManageSubmissionsUseCase {
    fun create(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?>
    fun submissions(actor: AcademicActor, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): Map<String, Any?>
    fun pendingFeedbackCount(actor: AcademicActor): Int
    fun submission(actor: AcademicActor, id: String): Map<String, Any?>
    fun update(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?>
    fun submit(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?>
    fun saveFeedback(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?>
    fun publishFeedback(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?>
}
