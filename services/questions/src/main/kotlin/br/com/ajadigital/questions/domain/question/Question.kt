package br.com.ajadigital.questions.domain.question

import java.security.MessageDigest
import java.time.Instant

data class QuestionImage(
    val fileId: String,
    val alt: String,
)

data class Question(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val subject: String,
    val topic: String,
    val difficulty: String,
    val institution: String,
    val year: Int,
    val statement: String,
    val options: List<String>,
    val correctOption: Int,
    val explanation: String,
    val image: QuestionImage?,
)

data class QuestionFilters(
    val q: String? = null,
    val subject: String? = null,
    val topic: String? = null,
    val difficulty: String? = null,
    val institution: String? = null,
    val year: Int? = null,
)

data class QuestionPage(
    val items: List<Question>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
)

data class QuestionSnapshot(
    val questionId: String,
    val revision: Int,
    val contentHash: String,
    val subject: String,
    val topic: String,
    val difficulty: String,
    val institution: String,
    val year: Int,
    val statement: String,
    val options: List<String>,
    val correctOption: Int,
    val explanation: String,
    val image: QuestionImage?,
)

fun Question.toSnapshot(): QuestionSnapshot {
    val fields =
        listOf(
            id,
            revision.toString(),
            createdAt.toString(),
            subject,
            topic,
            difficulty,
            institution,
            year.toString(),
            statement,
            options.size.toString(),
            *options.toTypedArray(),
            correctOption.toString(),
            explanation,
            if (image == null) "0" else "1",
            image?.fileId.orEmpty(),
            image?.alt.orEmpty(),
        )
    val canonical = fields.joinToString("") { "${it.length}:$it" }
    val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    return QuestionSnapshot(
        id,
        revision,
        hash,
        subject,
        topic,
        difficulty,
        institution,
        year,
        statement,
        options,
        correctOption,
        explanation,
        image,
    )
}
