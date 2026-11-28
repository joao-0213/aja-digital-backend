package br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@JsonIgnoreProperties(ignoreUnknown = false)
data class ModuleEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val title: JsonNode = MissingNode.getInstance(), val description: JsonNode = MissingNode.getInstance())
