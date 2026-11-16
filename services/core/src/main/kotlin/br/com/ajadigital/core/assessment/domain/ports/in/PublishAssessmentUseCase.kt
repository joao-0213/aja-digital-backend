package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface PublishAssessmentUseCase {
    fun publish(actor: AcademicActor, id: String, key: String, expectedRevision: Int, body: JsonNode): Map<String, Any?>
}
