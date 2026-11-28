package br.com.ajadigital.core.attendance.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = false)
data class AttendanceCreateRequest(val subjectId: String, val date: String, val time: String, val records: JsonNode)
