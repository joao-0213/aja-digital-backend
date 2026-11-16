package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface ManageAttemptsUseCase {
    fun practiceBlocked(actor: AcademicActor, questionId: String): Boolean
    fun start(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?>
    fun attempts(actor: AcademicActor, page: Int, pageSize: Int, sort: String, assessmentId: String?, studentId: String?): Map<String, Any?>
    fun attempt(actor: AcademicActor, id: String): Map<String, Any?>
    fun saveAnswers(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?>
    fun submit(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?>
    fun result(actor: AcademicActor, id: String): Map<String, Any?>
}
