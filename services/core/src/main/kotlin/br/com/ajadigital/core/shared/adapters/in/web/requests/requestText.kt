package br.com.ajadigital.core.shared.adapters.`in`.web.requests

import tools.jackson.databind.JsonNode

internal fun requestText(value: JsonNode): String? = if (value.isMissingNode) null else value.asText().also { require(value.isTextual) }
