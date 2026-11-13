package br.com.ajadigital.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties


@JsonIgnoreProperties(ignoreUnknown = false)
data class ModuleCreateRequest(val subjectId: String, val title: String, val description: String)
