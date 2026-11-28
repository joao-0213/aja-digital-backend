package br.com.ajadigital.core.assessment.adapters.serialization

import br.com.ajadigital.core.assessment.domain.models.AttemptPage
import br.com.ajadigital.core.assessment.domain.models.AttemptPrompt
import br.com.ajadigital.core.assessment.domain.models.AttemptQuestion
import br.com.ajadigital.core.assessment.domain.models.AttemptQuestionField
import br.com.ajadigital.core.assessment.domain.models.AttemptQuestionImage
import br.com.ajadigital.core.assessment.domain.models.AttemptQuestionImageField
import br.com.ajadigital.core.assessment.domain.models.AttemptRecord
import br.com.ajadigital.core.assessment.domain.models.AttemptResult
import br.com.ajadigital.core.assessment.domain.models.AttemptState

internal object AttemptRepresentation {
    private val questionFields = setOf("id", "subject", "topic", "difficulty", "institution", "year", "statement", "options", "image", "correct", "explanation")

    fun page(page: AttemptPage): Map<String, Any?> = linkedMapOf(
        "items" to page.items.map(::attempt),
        "page" to page.page,
        "pageSize" to page.pageSize,
        "total" to page.total,
    )

    fun attempt(attempt: AttemptRecord): Map<String, Any?> = linkedMapOf(
        "id" to attempt.id,
        "revision" to attempt.revision,
        "createdAt" to attempt.createdAt,
        "updatedAt" to attempt.updatedAt,
        "updatedBy" to attempt.updatedBy,
        "assessmentId" to attempt.assessmentId,
        "assessmentTitle" to attempt.assessmentTitle,
        "userId" to attempt.userId,
        "studentName" to attempt.studentName,
        "startedAt" to attempt.startedAt,
        "submittedAt" to (attempt.state as? AttemptState.Submitted)?.submittedAt?.toString(),
        "answers" to attempt.answers,
        "questionOrder" to attempt.questionOrder,
        "questions" to attempt.questions.map(::prompt),
        "questionsUpdatedAt" to attempt.questionsUpdatedAt,
    )

    fun result(result: AttemptResult): Map<String, Any?> = linkedMapOf(
        "attempt" to attempt(result.attempt),
        "receipt" to linkedMapOf("id" to result.receipt.id, "resourceId" to result.receipt.resourceId, "submittedAt" to result.receipt.submittedAt),
        "score" to result.score,
        "maxScore" to result.maxScore,
        "questions" to result.questions.map(::question),
    )

    fun readAttempt(document: Map<String, Any?>): AttemptRecord = AttemptRecord(
        id = document["id"] as String,
        revision = document["revision"] as Int,
        createdAt = document["createdAt"] as String,
        updatedAt = document["updatedAt"] as String,
        updatedBy = document["updatedBy"] as String?,
        assessmentId = document["assessmentId"] as String,
        assessmentTitle = document["assessmentTitle"] as String,
        userId = document["userId"] as String,
        studentName = document["studentName"] as String?,
        startedAt = document["startedAt"] as String,
        state = AttemptState.from(document["submittedAt"] as String?),
        answers = document["answers"] as Map<String, Int?>,
        questionOrder = document["questionOrder"] as List<String>,
        questions = (document["questions"] as List<Map<String, Any?>>).map { prompt(readQuestion(it)) },
        questionsUpdatedAt = document["questionsUpdatedAt"] as String?,
    )

    fun readResult(document: Map<String, Any?>): AttemptResult {
        val receipt = document["receipt"] as Map<String, Any?>
        return AttemptResult(
            attempt = readAttempt(document["attempt"] as Map<String, Any?>),
            receipt = br.com.ajadigital.core.assessment.domain.models.AttemptReceipt(
                receipt["id"] as String,
                receipt["resourceId"] as String,
                receipt["submittedAt"] as String,
            ),
            score = (document["score"] as Number).toDouble(),
            maxScore = (document["maxScore"] as Number).toDouble(),
            questions = (document["questions"] as List<Map<String, Any?>>).map(::readQuestion),
        )
    }

    fun promptQuestion(question: AttemptQuestion): AttemptPrompt = prompt(question)

    fun readQuestion(snapshot: Map<String, Any?>): AttemptQuestion {
        val fields = snapshot.keys.mapNotNull { key -> AttemptQuestionField.fromKey(key).takeIf { key in questionFields } }.toMutableList()
        if (AttemptQuestionField.ID !in fields) fields += AttemptQuestionField.ID
        val rawImage = snapshot["image"] as? Map<*, *>
        val image = rawImage?.let { value ->
            val imageFields = value.keys.filterIsInstance<String>().mapNotNull(AttemptQuestionImageField::fromKey)
            AttemptQuestionImage(imageFields, value["alt"] as? String, value["src"] as? String)
        }
        return AttemptQuestion(
            id = snapshot["id"] as String,
            fields = fields,
            year = snapshot["year"] as? Int,
            image = image,
            topic = snapshot["topic"] as? String,
            correct = snapshot["correct"] as? Int,
            options = snapshot["options"] as? List<String>,
            subject = snapshot["subject"] as? String,
            statement = snapshot["statement"] as? String,
            difficulty = snapshot["difficulty"] as? String,
            explanation = snapshot["explanation"] as? String,
            institution = snapshot["institution"] as? String,
        )
    }

    private fun prompt(question: AttemptPrompt): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        question.fields.forEach { field ->
            put(
                field.key,
                when (field) {
                    AttemptQuestionField.ID -> question.id
                    AttemptQuestionField.YEAR -> question.year
                    AttemptQuestionField.IMAGE -> question.image?.let(::image)
                    AttemptQuestionField.TOPIC -> question.topic
                    AttemptQuestionField.CORRECT, AttemptQuestionField.EXPLANATION -> error("Private answer fields are not part of an attempt prompt")
                    AttemptQuestionField.OPTIONS -> question.options
                    AttemptQuestionField.SUBJECT -> question.subject
                    AttemptQuestionField.STATEMENT -> question.statement
                    AttemptQuestionField.DIFFICULTY -> question.difficulty
                    AttemptQuestionField.INSTITUTION -> question.institution
                },
            )
        }
    }

    private fun question(question: AttemptQuestion): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        question.fields.forEach { field ->
            put(
                field.key,
                when (field) {
                    AttemptQuestionField.ID -> question.id
                    AttemptQuestionField.YEAR -> question.year
                    AttemptQuestionField.IMAGE -> question.image?.let(::image)
                    AttemptQuestionField.TOPIC -> question.topic
                    AttemptQuestionField.CORRECT -> question.correct
                    AttemptQuestionField.OPTIONS -> question.options
                    AttemptQuestionField.SUBJECT -> question.subject
                    AttemptQuestionField.STATEMENT -> question.statement
                    AttemptQuestionField.DIFFICULTY -> question.difficulty
                    AttemptQuestionField.EXPLANATION -> question.explanation
                    AttemptQuestionField.INSTITUTION -> question.institution
                },
            )
        }
    }

    private fun prompt(question: AttemptQuestion) = AttemptPrompt(
        question.id, question.fields.filter { it !in setOf(AttemptQuestionField.CORRECT, AttemptQuestionField.EXPLANATION) },
        question.year, question.image, question.topic, question.options, question.subject, question.statement, question.difficulty, question.institution,
    )

    private fun image(image: AttemptQuestionImage): Map<String, Any?> = LinkedHashMap<String, Any?>().apply {
        image.fields.forEach { field -> put(field.key, if (field == AttemptQuestionImageField.ALT) image.alt else image.src) }
    }
}
