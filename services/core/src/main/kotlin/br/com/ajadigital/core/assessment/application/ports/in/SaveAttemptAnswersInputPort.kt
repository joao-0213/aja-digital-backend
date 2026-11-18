package br.com.ajadigital.core.assessment.application.ports.`in`
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersDto

import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

interface SaveAttemptAnswersInputPort {
    fun saveAnswers(request: SaveAttemptAnswersDto): Map<String, Any?>
}
