package br.com.ajadigital.core.assessment.application.ports.`in`

import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersCommand
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface SaveAttemptAnswersInputPort {
    fun saveAnswers(request: SaveAttemptAnswersCommand): AttemptRecord
}
