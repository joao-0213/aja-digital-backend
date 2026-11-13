package br.com.ajadigital.core.application.identity



data class ProfileEditCommand(val expectedRevision: Int, val name: String?)
