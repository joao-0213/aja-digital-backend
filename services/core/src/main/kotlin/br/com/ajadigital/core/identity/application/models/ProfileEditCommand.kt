package br.com.ajadigital.core.identity.application.models

data class ProfileEditCommand(
    val expectedRevision: Int,
    val name: String?,
)
