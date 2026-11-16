package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface PublishSubmissionFeedbackUseCase {
    fun publishFeedback(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?>
}
