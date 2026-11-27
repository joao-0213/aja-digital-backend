package br.com.ajadigital.core.academic.application.models

import java.time.Instant
import java.util.Collections

class AcademicClassView(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String,
    val name: String,
    val code: String,
    val year: Int,
    val shift: String,
    val active: Boolean,
    /** Null means the caller's view omits studentIds; an empty list means none are enrolled. */
    studentIds: List<String>?,
) {
    val studentIds: List<String>? = studentIds?.let { Collections.unmodifiableList(it.toList()) }
}
