package br.com.ajadigital.core.identity.application.models

data class UpdateOwnProfileDto(
    val cookie: String?,
    val command: ProfileEditCommand,
)
