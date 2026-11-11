package br.com.ajadigital.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class OriginFilterTests {
    private val filter = OriginFilter("http://127.0.0.1:3000")

    @Test
    fun `allows terminal and configured origin requests`() {
        val terminalResponse = MockHttpServletResponse()
        val terminalChain = MockFilterChain()
        filter.doFilter(MockHttpServletRequest("GET", "/api/v1/health"), terminalResponse, terminalChain)
        assertNotNull(terminalChain.request)

        val request = MockHttpServletRequest("GET", "/api/v1/health").apply {
            addHeader("Origin", "http://127.0.0.1:3000")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        assertEquals("http://127.0.0.1:3000", response.getHeader("Access-Control-Allow-Origin"))
        assertNotNull(chain.request)
    }

    @Test
    fun `rejects unconfigured origin without cors headers`() {
        val request = MockHttpServletRequest("GET", "/api/v1/health").apply {
            addHeader("Origin", "https://untrusted.example")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        assertEquals(403, response.status)
        assertEquals("FORBIDDEN", response.contentAsString.let { Regex(""""code":"([^"]+)"""").find(it)?.groupValues?.get(1) })
        assertNull(chain.request)
        assertNull(response.getHeader("Access-Control-Allow-Origin"))
    }
}
