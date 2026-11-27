package br.com.ajadigital.core.assessment.domain.models

import java.util.Collections

class BankQuestionSnapshot(
    val questionId: String,
    val revision: Int,
    val contentHash: String,
    val subject: String,
    val topic: String,
    val difficulty: String,
    val institution: String,
    val year: Int,
    val statement: String,
    options: List<String>,
    val correctOption: Int,
    val explanation: String,
    val image: BankQuestionImage?,
) {
    val options: List<String> = Collections.unmodifiableList(options.toList())
}
