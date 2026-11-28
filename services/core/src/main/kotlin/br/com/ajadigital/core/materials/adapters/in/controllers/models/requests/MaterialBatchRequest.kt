package br.com.ajadigital.core.materials.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@JsonIgnoreProperties(ignoreUnknown = false)
data class MaterialBatchRequest(val moduleId: String, val expectedRevision: JsonNode = MissingNode.getInstance(), val items: List<MaterialCreateRequest>)
