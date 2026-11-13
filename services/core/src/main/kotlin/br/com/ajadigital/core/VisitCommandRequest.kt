package br.com.ajadigital.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class VisitCommandRequest(val publicationIds: List<String>)
