package br.com.ajadigital.core.assessment.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.`in`.SaveAttemptAnswersUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import tools.jackson.databind.JsonNode

class SaveAttemptAnswers(private val store: AttemptStore) : SaveAttemptAnswersUseCase {
    override fun saveAnswers(actor: AcademicActor, id: String, body: JsonNode): Map<String, Any?> {
        AttemptSupport.student(actor)
        AttemptSupport.id(id)
        require(body.isObject && AttemptSupport.fields(body) == setOf("expectedRevision", "answers"))
        val revision = AttemptSupport.integer(body, "expectedRevision").also { require(it > 0) }
        val node = body.get("answers")
        require(node.isObject)
        val answers = node.properties().associate { (questionId, answer) ->
            AttemptSupport.id(questionId)
            questionId to when {
                answer.isNull -> null
                answer.isIntegralNumber && answer.canConvertToInt() && answer.intValue() >= 0 -> answer.intValue()
                else -> throw IllegalArgumentException()
            }
        }
        return store.saveAnswers(actor.id, actor.role, actor.sessionTokenHash, id, revision, answers) ?: throw AcademicNotFound()
    }
}
