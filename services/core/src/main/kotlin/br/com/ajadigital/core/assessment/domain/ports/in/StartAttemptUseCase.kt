package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

interface StartAttemptUseCase {
    fun start(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?>
}
