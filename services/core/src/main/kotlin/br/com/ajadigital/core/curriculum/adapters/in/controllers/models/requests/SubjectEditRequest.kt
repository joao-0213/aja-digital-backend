package br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode

@JsonIgnoreProperties(ignoreUnknown = false)
data class SubjectEditRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val classId: JsonNode = MissingNode.getInstance(), val name: JsonNode = MissingNode.getInstance(), val teacherIds: JsonNode = MissingNode.getInstance(), val color: JsonNode = MissingNode.getInstance(), val icon: JsonNode = MissingNode.getInstance(), val description: JsonNode = MissingNode.getInstance())
