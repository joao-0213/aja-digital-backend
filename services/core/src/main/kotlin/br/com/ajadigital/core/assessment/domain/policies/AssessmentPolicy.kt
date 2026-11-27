package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.assessment.domain.exceptions.AssessmentAuthoringFailure.Forbidden
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionContent
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot

internal object AssessmentPolicy {
    private val identifier = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val fingerprint = Regex("^[a-f0-9]{64}$")
    private val authoredImage = Regex("^/questions/[A-Za-z0-9_-]+\\.(?:svg|png|jpe?g|webp)$")
    private val difficulties = setOf("easy", "medium", "hard")

    fun id(value: String): String = value.also { require(validId(it)) }
    fun validId(value: String) = identifier.matches(value)

    fun validateCommand(key: String, hash: String) {
        require(key.length in 8..128 && fingerprint.matches(hash))
    }

    fun validateDraft(draft: AssessmentDraft) {
        id(draft.moduleId)
        validateTitle(draft.title)
        validateDescription(draft.description)
        validateMaxGrade(draft.maxGrade)
    }

    fun validateTitle(value: String) { require(value.isNotBlank() && value.length <= 160) }
    fun validateDescription(value: String) { require(value.length <= 10000) }
    fun validateMaxGrade(value: Double?) { require(value == null || value > 0 && value.isFinite()) }

    fun validateAuthoredQuestion(content: AssessmentQuestionContent) {
        require(content.difficulty in difficulties && content.year >= 0)
        require(content.options.size in 2..20 && content.correct in content.options.indices)
        require(content.image?.let { authoredImage.matches(it.src) } != false)
    }

    fun validBankSnapshot(snapshot: BankQuestionSnapshot): Boolean =
        snapshot.revision >= 1 && fingerprint.matches(snapshot.contentHash) &&
            snapshot.options.size in 2..20 && snapshot.correctOption in snapshot.options.indices &&
            snapshot.difficulty in difficulties && snapshot.image?.fileId?.let(::validId) != false

    fun writer(role: String, active: Boolean) {
        if (!active || role !in setOf("admin", "teacher")) throw Forbidden()
    }

    fun reader(role: String, active: Boolean, emailConfirmed: Boolean) {
        if (!active || role !in setOf("admin", "teacher", "student") ||
            role == "student" && !emailConfirmed
        ) throw Forbidden()
    }
}
