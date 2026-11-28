package br.com.ajadigital.core.forum.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@JsonIgnoreProperties(ignoreUnknown = false)
data class TopicSolutionRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val replyId: JsonNode = MissingNode.getInstance())
