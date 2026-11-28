package br.com.ajadigital.core.shared.adapters.`in`.web.requests

import tools.jackson.databind.JsonNode

internal fun requestRevision(value: JsonNode): Int = value.intValue().also { require(value.isIntegralNumber && value.canConvertToInt() && it > 0) }
