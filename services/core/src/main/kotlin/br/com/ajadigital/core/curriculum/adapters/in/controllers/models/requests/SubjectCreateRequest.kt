package br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class SubjectCreateRequest(val classId: String, val name: String, val teacherIds: List<String>, val color: String, val icon: String = "book", val description: String)
