package br.com.ajadigital.core.academic.application.contracts

import java.time.Instant

data class DashboardClassProjection(
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
    val studentCount: Int,
)
