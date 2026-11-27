package br.com.ajadigital.core.assessment.application.models

import java.util.Collections

class AssessmentResults(items: List<AssessmentDetails>, val page: Int, val pageSize: Int, val total: Long) {
    val items: List<AssessmentDetails> = Collections.unmodifiableList(items.toList())
}
