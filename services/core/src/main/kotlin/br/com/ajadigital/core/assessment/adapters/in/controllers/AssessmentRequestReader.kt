package br.com.ajadigital.core.assessment.adapters.`in`.controllers

import br.com.ajadigital.core.assessment.domain.models.AssessmentKind
import br.com.ajadigital.core.assessment.application.models.AssessmentChanges
import br.com.ajadigital.core.assessment.application.models.AssessmentQuestionSelections
import br.com.ajadigital.core.assessment.application.models.AssessmentQuestionSelections.Selection
import br.com.ajadigital.core.assessment.application.models.CreateAssessmentCommand
import br.com.ajadigital.core.assessment.application.models.PublishAssessmentCommand
import br.com.ajadigital.core.assessment.application.models.UpdateAssessmentCommand
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionContent
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionImage
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.toRequestValue
import br.com.ajadigital.core.shared.application.contracts.FieldChange
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException

@Component
class AssessmentRequestReader(private val mapper: ObjectMapper) {
    private val createFields = setOf("moduleId", "title", "description", "kind", "maxGrade", "deadline", "required", "allowLate", "questions")
    private val editFields = createFields + setOf("expectedRevision", "confirmResetDrafts")
    private val authoredFields = setOf("subject", "topic", "difficulty", "institution", "year", "statement", "options", "image", "correct", "explanation")

    fun create(actor: AuthenticatedActor, key: String, json: JsonNode): CreateAssessmentCommand {
        val body = json.toRequestValue().asObject()
        require(body.keys == createFields)
        val draft = AssessmentDraft(
            body.text("moduleId"), body.text("title").trim(), body.text("description"), AssessmentKind.parse(body.text("kind")),
            number(body["maxGrade"]), timestamp(body["deadline"]), body.boolean("required"), body.boolean("allowLate"),
        )
        return CreateAssessmentCommand(actor, key, fingerprint(body), draft, questions(body["questions"]))
    }

    fun update(actor: AuthenticatedActor, id: String, key: String, json: JsonNode): UpdateAssessmentCommand {
        val body = json.toRequestValue().asObject()
        val expectedRevision = integer(body["expectedRevision"])
        // The input port raises deferred errors after the same visibility, replay and module checks as before.
        val changes = parseOrNull {
            require(body.keys.all { it in editFields })
            AssessmentChanges(
                body.change("moduleId") { body.text("moduleId") },
                body.change("title") { body.text("title").trim() },
                body.change("description") { body.text("description") },
                body.change("kind") { AssessmentKind.parse(body.text("kind")) },
                body.change("maxGrade") { number(body["maxGrade"]) },
                body.change("deadline") { timestamp(body["deadline"]) },
                body.change("required") { body.boolean("required") },
                body.change("allowLate") { body.boolean("allowLate") },
            )
        }
        val selected = body.change("questions") { parseOrNull { questions(body["questions"]) } }
        val reset = if (body.containsKey("confirmResetDrafts")) body["confirmResetDrafts"] as? Boolean else false
        return UpdateAssessmentCommand(actor, id, key, fingerprint(body), expectedRevision, changes, selected, reset)
    }

    fun publish(actor: AuthenticatedActor, id: String, key: String, json: JsonNode): PublishAssessmentCommand {
        val body = json.toRequestValue().asObject()
        require(body.keys == setOf("expectedRevision"))
        return PublishAssessmentCommand(actor, id, key, integer(body["expectedRevision"]), fingerprint(body))
    }

    private fun questions(value: Any?): AssessmentQuestionSelections {
        val list = value as? List<*> ?: throw IllegalArgumentException()
        require(list.size <= 100)
        return AssessmentQuestionSelections(list.map { item ->
            val entry = item.asObject()
            when (entry.text("source")) {
                "bank" -> {
                    require(entry.keys == setOf("source", "questionId"))
                    Selection.Bank(entry.text("questionId"))
                }
                "authored" -> {
                    require(entry.keys == setOf("source", "clientKey", "questionId", "question"))
                    val questionId = entry["questionId"]?.let { it as? String ?: throw IllegalArgumentException() }
                    Selection.Authored(entry.text("clientKey"), questionId, authored(entry["question"]))
                }
                else -> throw IllegalArgumentException()
            }
        })
    }

    private fun authored(value: Any?): AssessmentQuestionContent {
        val question = value.asObject()
        require(question.keys == authoredFields)
        val options = question["options"] as? List<*> ?: throw IllegalArgumentException()
        require(options.size in 2..20 && options.all { it is String })
        val image = question["image"]?.asObject()?.let {
            require(it.keys == setOf("src", "alt"))
            AssessmentQuestionImage(it.text("src"), it.text("alt"))
        }
        return AssessmentQuestionContent(
            question.text("subject"), question.text("topic"), question.text("difficulty"), question.text("institution"),
            integer(question["year"]), question.text("statement"), options.filterIsInstance<String>(), image,
            integer(question["correct"]), question.text("explanation"),
        )
    }

    private fun fingerprint(body: Map<*, *>): String = MessageDigest.getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }

    private fun Any?.asObject(): Map<*, *> = this as? Map<*, *> ?: throw IllegalArgumentException()
    private fun Map<*, *>.text(key: String): String = this[key] as? String ?: throw IllegalArgumentException()
    private fun Map<*, *>.boolean(key: String): Boolean = this[key] as? Boolean ?: throw IllegalArgumentException()

    private fun integer(value: Any?): Int = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: throw IllegalArgumentException()
        else -> throw IllegalArgumentException()
    }

    private fun number(value: Any?): Double? = when (value) {
        null -> null
        is Number -> value.toDouble()
        else -> throw IllegalArgumentException()
    }

    private fun timestamp(value: Any?): Instant? = when (value) {
        null -> null
        is String -> try {
            Instant.parse(value)
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException(error)
        }
        else -> throw IllegalArgumentException()
    }

    private fun <T> Map<*, *>.change(key: String, parse: () -> T): FieldChange<T> =
        if (containsKey(key)) FieldChange.Set(parse()) else FieldChange.Unchanged

    private fun <T> parseOrNull(parse: () -> T): T? = try {
        parse()
    } catch (_: IllegalArgumentException) {
        null
    }
}
