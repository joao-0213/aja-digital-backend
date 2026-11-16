package br.com.ajadigital.core.identity.domain.models



data class ProfileEditCommand(val expectedRevision: Int, val name: String?)
