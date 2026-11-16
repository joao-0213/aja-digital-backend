package br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class ClassCreateRequest(val name: String, val code: String, val year: Int, val shift: String)
