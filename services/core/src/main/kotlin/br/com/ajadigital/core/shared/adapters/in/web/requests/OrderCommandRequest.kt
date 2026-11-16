package br.com.ajadigital.core.shared.adapters.`in`.web.requests

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.MissingNode


@JsonIgnoreProperties(ignoreUnknown = false)
data class OrderCommandRequest(val expectedRevision: JsonNode = MissingNode.getInstance(), val items: List<OrderItemRequest>)
