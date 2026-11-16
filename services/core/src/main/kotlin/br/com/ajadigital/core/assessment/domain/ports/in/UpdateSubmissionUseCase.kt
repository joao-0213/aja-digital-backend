package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface UpdateSubmissionUseCase {
    fun update(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?>
}
