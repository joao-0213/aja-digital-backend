package br.com.ajadigital.core.announcements.adapters.`in`.controllers.models.requests

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = false)
data class AnnouncementRevisionRequest(val expectedRevision: JsonNode = MissingNode.getInstance())
