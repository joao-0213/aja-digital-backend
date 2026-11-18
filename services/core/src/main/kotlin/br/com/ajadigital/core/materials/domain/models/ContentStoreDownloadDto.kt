package br.com.ajadigital.core.materials.domain.models


data class ContentStoreDownloadDto(val id: String, val actorId: String, val role: String, val sessionHash: String, val disposition: String?)
