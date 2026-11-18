package br.com.ajadigital.core.assessment.application.usecases
import br.com.ajadigital.core.assessment.application.models.*

import br.com.ajadigital.core.assessment.domain.models.AttemptStoreSaveAnswersDto

import br.com.ajadigital.core.assessment.application.models.SaveAttemptAnswersDto

import br.com.ajadigital.core.assessment.domain.policies.AttemptPolicy
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveAttemptAnswersInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor

class SaveAttemptAnswersUseCase(private val store: AttemptStoreOutputPort) : SaveAttemptAnswersInputPort {
    override fun saveAnswers(request: SaveAttemptAnswersDto): Map<String, Any?> {
                val (actor, id, body) = request
        AttemptPolicy.student(actor)
        AttemptPolicy.id(id)
        require(AttemptPolicy.isObject(body) && AttemptPolicy.fields(body) == setOf("expectedRevision", "answers"))
        val revision = AttemptPolicy.integer(body, "expectedRevision").also { require(it > 0) }
        val node = AttemptPolicy.value(body, "answers")
        val answerMap = node as? Map<*, *> ?: throw IllegalArgumentException()
        val answers = answerMap.entries.associate { (rawQuestionId, answer) ->
            val questionId = rawQuestionId as? String ?: throw IllegalArgumentException()
            AttemptPolicy.id(questionId)
            questionId to when {
                answer == null -> null
                AttemptPolicy.integerValue(answer)?.let { it >= 0 } == true -> AttemptPolicy.integerValue(answer)
                else -> throw IllegalArgumentException()
            }
        }
        return store.saveAnswers(AttemptStoreSaveAnswersDto(actor.id, actor.role, actor.sessionValidationReference, id, revision, answers)) ?: throw AcademicNotFound()
    }
}
