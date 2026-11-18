package br.com.ajadigital.core.shared.adapters.`in`.web.requests

import tools.jackson.databind.JsonNode

internal fun JsonNode.toRequestValue(): Any? = when {
    isNull -> null
    isObject -> properties().associateTo(linkedMapOf()) { it.key to it.value.toRequestValue() }
    isArray -> List(size()) { get(it).toRequestValue() }
    isTextual -> asString()
    isBoolean -> booleanValue()
    isNumber -> numberValue()
    else -> throw IllegalArgumentException()
}
