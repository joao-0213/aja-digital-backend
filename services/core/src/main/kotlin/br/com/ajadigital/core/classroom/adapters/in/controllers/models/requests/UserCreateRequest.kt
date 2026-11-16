package br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class UserCreateRequest(val name: String, val email: String, val role: String)
