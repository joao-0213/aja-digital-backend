package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.assessment.domain.models.SubmissionAttachment
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import java.net.URI
import java.math.BigDecimal
import java.time.Instant

internal object SubmissionPolicy {
    fun isValidFeedbackGrade(grade: BigDecimal?, maxGrade: BigDecimal?): Boolean =
        grade == null || (maxGrade != null && grade <= maxGrade)

    fun canPublishFeedback(comment: String, grade: BigDecimal?): Boolean =
        !(comment.isBlank() && grade == null)

    fun areStagedFilesValid(fileIds: List<String>, lockedFileExpiries: Map<String, Instant>, now: Instant): Boolean =
        lockedFileExpiries.size == fileIds.size && fileIds.all { fileId -> lockedFileExpiries[fileId]?.isAfter(now) == true }

    fun attachments(node: List<SubmissionAttachment>): List<SubmissionAttachment> {
        require(node.size <= 5)
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
                    require(uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && uri.rawUserInfo == null && uri.host != null)
                    SubmissionAttachment("link", name, url = url)
                }
                else -> throw IllegalArgumentException()
            }
        }
    }

    fun student(actor: AuthenticatedActor) {
        if (!actor.active || actor.role != "student" || actor.emailConfirmedAt == null) throw AcademicForbidden()
    }

    fun reader(actor: AuthenticatedActor) {
        if (!actor.active || actor.role !in setOf("student", "teacher", "admin") || actor.role == "student" && actor.emailConfirmedAt == null) {
            throw AcademicForbidden()
        }
    }

    fun writer(actor: AuthenticatedActor) {
        if (!actor.active || actor.role !in setOf("teacher", "admin")) throw AcademicForbidden()
    }

    fun validKey(value: String) {
        require(value.length in 8..128)
    }

    fun id(value: String) {
        require(validId(value))
    }

    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))

}
