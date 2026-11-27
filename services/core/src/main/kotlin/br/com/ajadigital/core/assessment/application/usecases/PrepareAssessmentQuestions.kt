package br.com.ajadigital.core.assessment.application.usecases

import br.com.ajadigital.core.assessment.application.models.AssessmentQuestionSelections
import br.com.ajadigital.core.assessment.application.models.AssessmentQuestionSelections.Selection
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionContent
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionImage
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestionView
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.application.models.QuestionSnapshotQuery
import br.com.ajadigital.core.assessment.domain.policies.AssessmentPolicy
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import java.util.UUID

internal class PrepareAssessmentQuestions(private val provider: QuestionSnapshotProviderOutputPort) {
    fun identify(
        selections: AssessmentQuestionSelections,
        prior: List<AssessmentQuestionView>,
    ): List<AssessmentQuestion> {
        val current = prior.associateBy { it.id }
        val ids = mutableSetOf<String>()
        val clientKeys = mutableSetOf<String>()
        require(selections.items.size <= 100)
        return selections.items.map { selection ->
            when (selection) {
                is Selection.Bank -> {
                    val id = AssessmentPolicy.id(selection.questionId)
                    require(ids.add(id))
                    require(current[id]?.origin in setOf(null, "bank"))
                    AssessmentQuestion(id, "bank", null)
                }
                is Selection.Authored -> {
                    require(clientKeys.add(AssessmentPolicy.id(selection.clientKey)))
                    val id = selection.questionId?.let {
                        require(current[it]?.origin == "author")
                        AssessmentPolicy.id(it)
                    } ?: "author-${UUID.randomUUID()}"
                    require(ids.add(id))
                    AssessmentPolicy.validateAuthoredQuestion(selection.content)
                    AssessmentQuestion(id, "author", selection.content, 1)
                }
            }
        }
    }

    fun capture(entries: List<AssessmentQuestion>, existingIds: Set<String>): List<AssessmentQuestion> {
        val needed = entries.filter { it.source == "bank" && it.id !in existingIds }.map { it.id }
        if (needed.isEmpty()) return entries
        val fetched = provider.snapshots(QuestionSnapshotQuery(needed))
        val byId = fetched.associateBy(BankQuestionSnapshot::questionId)
        if (fetched.size != needed.size || byId.keys != needed.toSet()) throw QuestionsUnavailable()
        return entries.map { entry ->
            val snapshot = byId[entry.id] ?: return@map entry
            if (!AssessmentPolicy.validBankSnapshot(snapshot)) throw QuestionsUnavailable()
            val content = AssessmentQuestionContent(
                snapshot.subject, snapshot.topic, snapshot.difficulty, snapshot.institution, snapshot.year, snapshot.statement,
                snapshot.options, snapshot.image?.let { AssessmentQuestionImage("/api/v1/files/${it.fileId}/content", it.alt) },
                snapshot.correctOption, snapshot.explanation,
            )
            entry.copy(snapshot = content, sourceRevision = snapshot.revision, contentHash = snapshot.contentHash)
        }
    }
}
