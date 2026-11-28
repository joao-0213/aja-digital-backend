package br.com.ajadigital.core.assessment.domain.models

import java.util.Collections

class SubmissionPage(
    items: List<SubmissionView>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
) {
    val items: List<SubmissionView> = Collections.unmodifiableList(items.toList())

    init {
        require(page > 0 && pageSize in 1..MAX_PAGE_SIZE && items.size <= pageSize && total >= 0)
    }
}

private const val MAX_PAGE_SIZE = 100
