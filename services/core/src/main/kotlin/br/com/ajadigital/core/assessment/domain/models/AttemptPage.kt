package br.com.ajadigital.core.assessment.domain.models

class AttemptPage(
    items: List<AttemptRecord>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
) {
    val items: List<AttemptRecord> = java.util.Collections.unmodifiableList(items.toList())
}
