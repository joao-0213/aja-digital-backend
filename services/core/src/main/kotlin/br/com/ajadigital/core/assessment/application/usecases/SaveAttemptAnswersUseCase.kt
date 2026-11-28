package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AttemptAnswerSaveCommand
import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersCommand
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveAttemptAnswersInputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentNotFound
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class SaveAttemptAnswersUseCase(private val store: AttemptStoreOutputPort) : SaveAttemptAnswersInputPort {
    override fun saveAnswers(request: SaveAttemptAnswersCommand): AttemptRecord {
        val (actor, id, body) = request
        AttemptPolicy.student(actor.role, actor.active, actor.emailConfirmedAt != null)
        AttemptPolicy.id(id)
        require(body != null && body.fieldsValid && body.answersValid)
        val revision = body.expectedRevision?.also { require(it > 0) } ?: throw IllegalArgumentException()
        val answers = body.answers ?: throw IllegalArgumentException()
        answers.forEach { (questionId, answer) ->
            AttemptPolicy.id(questionId)
            require(answer == null || answer >= 0)
        }
        return store.saveAnswers(AttemptAnswerSaveCommand(actor.id, actor.role, actor.sessionValidationReference, id, revision, answers)) ?: throw AssessmentNotFound()
    }
}
