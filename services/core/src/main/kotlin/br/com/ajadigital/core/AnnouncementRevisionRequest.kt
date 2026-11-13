package br.com.ajadigital.core

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode


@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = false)
data class AnnouncementRevisionRequest(val expectedRevision: JsonNode = MissingNode.getInstance())
