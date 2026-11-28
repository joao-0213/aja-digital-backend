package br.com.ajadigital.core.academic.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@JsonIgnoreProperties(ignoreUnknown = false)
data class UserEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val name: JsonNode = MissingNode.getInstance(), val email: JsonNode = MissingNode.getInstance())
