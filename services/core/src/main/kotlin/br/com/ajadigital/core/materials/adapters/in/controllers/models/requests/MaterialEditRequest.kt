package br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode


@JsonIgnoreProperties(ignoreUnknown = false)
data class MaterialEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val title: JsonNode = MissingNode.getInstance(), val duration: JsonNode = MissingNode.getInstance(), val content: JsonNode = MissingNode.getInstance(), val videoUrl: JsonNode = MissingNode.getInstance())
