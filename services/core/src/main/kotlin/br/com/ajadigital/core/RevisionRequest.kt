package br.com.ajadigital.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode


@JsonIgnoreProperties(ignoreUnknown = false)
data class RevisionRequest(val expectedRevision: JsonNode = MissingNode.getInstance())
