package br.com.ajadigital.core.academic.application.models

import java.time.Instant

data class AcademicUserView(
    val id: String,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val updatedBy: String,
    val name: String,
    val email: String,
    val role: String,
    val active: Boolean,
    val emailConfirmedAt: Instant?,
)
