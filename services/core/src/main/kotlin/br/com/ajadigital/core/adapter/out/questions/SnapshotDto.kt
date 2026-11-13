package br.com.ajadigital.core.adapter.out.questions

import br.com.ajadigital.core.ports.out.BankQuestionImage
import br.com.ajadigital.core.ports.out.BankQuestionSnapshot


internal data class SnapshotDto(
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
    val image: ImageDto?,
) {
    fun toSnapshot() = BankQuestionSnapshot(questionId, revision, contentHash, subject, topic, difficulty, institution, year, statement, options, correctOption, explanation, image?.let { BankQuestionImage(it.fileId, it.alt) })
}
