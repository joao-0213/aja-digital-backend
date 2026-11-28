package br.com.ajadigital.core.shared.application.contracts

import java.util.Collections

class Page<T>(
    items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
) {
    val items: List<T> = Collections.unmodifiableList(items.toList())

    init {
        require(page >= 1)
        require(pageSize >= 1)
        require(total >= 0)
    }
}
