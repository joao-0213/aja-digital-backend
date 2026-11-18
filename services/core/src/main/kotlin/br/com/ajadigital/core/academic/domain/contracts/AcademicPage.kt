package br.com.ajadigital.core.academic.domain.contracts

import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet

/** Transitional immutable pagination result exported to existing cross-feature callers. */
class AcademicPage(
    items: List<Map<String, Any?>>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
) {
    val items: List<Map<String, Any?>> = Collections.unmodifiableList(
        items.map { row ->
            Collections.unmodifiableMap(LinkedHashMap(row.mapValues { (_, value) -> immutableCollections(value) }))
        },
    )

    init {
        require(page >= 1) { "page must be at least 1" }
        require(pageSize >= 1) { "pageSize must be at least 1" }
        require(total >= 0) { "total must not be negative" }
    }

    private fun immutableCollections(value: Any?): Any? = when (value) {
        is Map<*, *> -> Collections.unmodifiableMap(LinkedHashMap<Any?, Any?>().apply {
            value.forEach { (key, nested) -> put(key, immutableCollections(nested)) }
        })
        is List<*> -> Collections.unmodifiableList(value.map(::immutableCollections))
        is Set<*> -> Collections.unmodifiableSet(LinkedHashSet(value.map(::immutableCollections)))
        else -> value
    }
}
