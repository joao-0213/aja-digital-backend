package br.com.ajadigital.core.shared.adapters.`in`.web.requests

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class RequestValueTests {
    @Test
    fun `JSON objects preserve order and nested values`() {
        val value = JsonMapper.builder().build()
            .readTree("""{"first":[1,null,{"enabled":true}],"second":1.5}""")
            .toRequestValue()

        assertEquals(
            linkedMapOf("first" to listOf(1, null, mapOf("enabled" to true)), "second" to 1.5),
            value,
        )
    }
}
