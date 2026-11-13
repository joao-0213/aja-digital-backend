package br.com.ajadigital.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode


@JsonIgnoreProperties(ignoreUnknown = false)
data class ClassEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val name: JsonNode = MissingNode.getInstance(), val code: JsonNode = MissingNode.getInstance(), val year: JsonNode = MissingNode.getInstance(), val shift: JsonNode = MissingNode.getInstance())
