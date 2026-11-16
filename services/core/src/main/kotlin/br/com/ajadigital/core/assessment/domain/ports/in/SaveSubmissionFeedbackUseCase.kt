package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface SaveSubmissionFeedbackUseCase {
    fun saveFeedback(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?>
}
