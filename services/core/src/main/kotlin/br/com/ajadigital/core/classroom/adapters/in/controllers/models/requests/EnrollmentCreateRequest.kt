package br.com.ajadigital.core.classroom.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class EnrollmentCreateRequest(val classId: String, val email: String)
