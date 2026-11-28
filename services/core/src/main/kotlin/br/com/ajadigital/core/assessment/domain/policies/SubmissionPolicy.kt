package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentForbidden
import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import java.math.BigDecimal
import java.net.URI
import java.time.Instant

internal object SubmissionPolicy {
    fun isValidFeedbackGrade(
        grade: BigDecimal?,
        maxGrade: BigDecimal?,
    ): Boolean = grade == null || (maxGrade != null && grade <= maxGrade)

    fun canPublishFeedback(
        comment: String,
        grade: BigDecimal?,
    ): Boolean = !(comment.isBlank() && grade == null)

    fun areStagedFilesValid(
        fileIds: List<String>,
        lockedFileExpiries: Map<String, Instant>,
        now: Instant,
    ): Boolean = lockedFileExpiries.size == fileIds.size && fileIds.all { fileId -> lockedFileExpiries[fileId]?.isAfter(now) == true }

    fun attachments(node: List<SubmissionAttachment>): List<SubmissionAttachment> {
        require(node.size <= MAX_ATTACHMENTS)
        val ids = mutableSetOf<String>()
        return node.map { item ->
            val name = item.name.also { require(it.isNotEmpty()) }
            when (item.kind) {
                "file" -> {
                    require(item.fileId != null && item.url == null)
                    val fileId = item.fileId.also(::id)
                    require(ids.add(fileId))
                    SubmissionAttachment("file", name, fileId = fileId)
                }
                "link" -> {
                    require(item.url != null && item.fileId == null)
                    val url = item.url
                    val uri = runCatching { URI(url) }.getOrNull() ?: throw IllegalArgumentException()
                    require(
                        uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && uri.rawUserInfo == null && uri.host != null,
                    )
                    SubmissionAttachment("link", name, url = url)
                }
                else -> throw IllegalArgumentException()
            }
        }
    }

    fun student(
        role: String,
        active: Boolean,
        emailConfirmed: Boolean,
    ) {
        if (!active || role != "student" || !emailConfirmed) throw AssessmentForbidden()
    }

    fun reader(
        role: String,
        active: Boolean,
        emailConfirmed: Boolean,
    ) {
        val unconfirmedStudent = role == "student" && !emailConfirmed
        if (!active || role !in setOf("student", "teacher", "admin") || unconfirmedStudent) {
            throw AssessmentForbidden()
        }
    }

    fun writer(
        role: String,
        active: Boolean,
    ) {
        if (!active || role !in setOf("teacher", "admin")) throw AssessmentForbidden()
    }

    fun validKey(value: String) {
        require(value.length in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH)
    }

    fun id(value: String) {
        require(validId(value))
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
}

private const val MIN_IDEMPOTENCY_KEY_LENGTH = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128

private const val MAX_ATTACHMENTS = 5
