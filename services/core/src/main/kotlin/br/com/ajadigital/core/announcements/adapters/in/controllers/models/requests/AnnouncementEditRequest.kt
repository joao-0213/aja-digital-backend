package br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = false)
data class AnnouncementEditRequest(
    val expectedRevision: JsonNode = MissingNode.getInstance(),
    val classId: JsonNode = MissingNode.getInstance(),
    val subjectId: JsonNode = MissingNode.getInstance(),
    val title: JsonNode = MissingNode.getInstance(),
    val body: JsonNode = MissingNode.getInstance(),
    val notify: JsonNode = MissingNode.getInstance(),
    val occurrence: JsonNode = MissingNode.getInstance(),
)
