package br.com.ajadigital.core.identity.domain.exceptions

class ProfileRevisionConflict(
    val currentRevision: Int,
) : RuntimeException()
