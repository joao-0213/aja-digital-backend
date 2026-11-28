package br.com.ajadigital.core.identity.application.models

data class UpdateIdentityProfileDto(
    val sessionTokenHash: String,
    val expectedRevision: Int,
    val name: String?,
)
