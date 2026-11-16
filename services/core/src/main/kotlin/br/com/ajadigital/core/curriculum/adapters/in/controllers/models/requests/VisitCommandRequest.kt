package br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class VisitCommandRequest(val publicationIds: List<String>)
