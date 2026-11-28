package br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = false)
data class AnnouncementCreateRequest(
    val classId: String,
    val subjectId: JsonNode = MissingNode.getInstance(),
    val title: String,
    val body: String,
    val notify: JsonNode = MissingNode.getInstance(),
    val occurrence: JsonNode = MissingNode.getInstance(),
)
