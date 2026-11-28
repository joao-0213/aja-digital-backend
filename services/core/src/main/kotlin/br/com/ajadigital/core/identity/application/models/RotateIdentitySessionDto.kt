package br.com.ajadigital.core.identity.application.models

import br.com.ajadigital.core.identity.domain.models.SessionRecord

data class RotateIdentitySessionDto(
    val record: SessionRecord,
    val previousTokenHash: String?,
    val expectedPasswordHash: String,
)
