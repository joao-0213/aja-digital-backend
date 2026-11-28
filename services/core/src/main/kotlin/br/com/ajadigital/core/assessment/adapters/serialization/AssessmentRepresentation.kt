package br.com.ajadigital.core.assessment.adapters.serialization

import br.com.ajadigital.core.assessment.application.models.AssessmentDetails
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentKind
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionContent
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionImage
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionView
import br.com.ajadigital.core.assessment.domain.models.AssessmentRecord
import br.com.ajadigital.core.assessment.domain.models.AssessmentState
import tools.jackson.databind.JsonNode
import java.time.Instant

internal object AssessmentRepresentation {
    fun response(details: AssessmentDetails): Map<String, Any?> = facts(details.record).apply {
        when (details) {
            is AssessmentDetails.Student -> {
                put("questionCount", details.questionCount)
                put("assigned", details.assigned)
            }
            is AssessmentDetails.Staff -> {
                put("questionIds", details.questions.map { it.id })
                put("questions", details.questions.map { question(it.id, it.origin, it.content) })
                put("hasSubmissions", details.hasSubmissions)
            }
        }
    }

    fun facts(record: AssessmentRecord): LinkedHashMap<String, Any?> {
        val draft = record.draft
        return linkedMapOf(
            "id" to record.id,
            "revision" to record.revision,
            "createdAt" to record.createdAt.toString(),
            "updatedAt" to record.updatedAt.toString(),
            "updatedBy" to record.updatedBy,
            "moduleId" to draft.moduleId,
            "title" to draft.title,
            "description" to draft.description,
            "kind" to draft.kind.value,
            "maxGrade" to draft.maxGrade,
            "deadline" to draft.deadline?.toString(),
            "required" to draft.required,
            "allowLate" to draft.allowLate,
            "status" to record.state.label,
            "publishedAt" to record.state.publishedAt?.toString(),
        )
    }

    fun question(
        id: String,
        origin: String,
        content: AssessmentQuestionContent,
    ): Map<String, Any?> = // Preserve PostgreSQL JSONB key order in the historical question receipts.
        linkedMapOf(
            "id" to id,
            "year" to content.year,
            "image" to content.image?.let { linkedMapOf("alt" to it.alt, "src" to it.src) },
            "topic" to content.topic,
            "origin" to origin,
            "correct" to content.correct,
            "options" to content.options,
            "subject" to content.subject,
            "statement" to content.statement,
            "difficulty" to content.difficulty,
            "explanation" to content.explanation,
            "institution" to content.institution,
        )

    fun readStaff(node: JsonNode): AssessmentDetails.Staff {
        val draft =
            AssessmentDraft(
                node.text("moduleId"),
                node.text("title"),
                node.text("description"),
                AssessmentKind.parse(node.text("kind")),
                node.get("maxGrade")?.takeUnless { it.isNull }?.doubleValue(),
                node.timestamp("deadline"),
                node.required("required").booleanValue(),
                node.required("allowLate").booleanValue(),
            )
        val record =
            AssessmentRecord(
                node.text("id"),
                node.required("revision").intValue(),
                Instant.parse(node.text("createdAt")),
                Instant.parse(node.text("updatedAt")),
                node.get("updatedBy")?.stringValue(),
                draft,
                state(node.text("status"), node.timestamp("publishedAt")),
            )
        return AssessmentDetails.Staff(
            record,
            node.array("questions").map {
                AssessmentQuestionView(it.text("id"), it.text("origin"), readQuestion(it))
            },
            node.required("hasSubmissions").booleanValue(),
        )
    }

    fun readQuestion(node: JsonNode): AssessmentQuestionContent = AssessmentQuestionContent(
        node.text("subject"),
        node.text("topic"),
        node.text("difficulty"),
        node.text("institution"),
        node.required("year").intValue(),
        node.text("statement"),
        node.array("options").map { checkNotNull(it.stringValue()) },
        node.get("image")?.takeUnless { it.isNull }?.let { AssessmentQuestionImage(it.text("src"), it.text("alt")) },
        node.required("correct").intValue(),
        node.text("explanation"),
    )

    fun state(
        status: String,
        publishedAt: Instant?,
    ): AssessmentState = when (status) {
        "draft" -> AssessmentState.Draft
        "published" -> AssessmentState.Published(checkNotNull(publishedAt))
        else -> error("Unexpected assessment status '$status'")
    }

    private fun JsonNode.text(name: String): String = checkNotNull(required(name).stringValue())

    private fun JsonNode.timestamp(name: String): Instant? = get(name)?.stringValue()?.let(Instant::parse)

    private fun JsonNode.array(name: String): List<JsonNode> {
        val node = required(name)
        check(node.isArray)
        return List(node.size()) { node[it] }
    }
}
