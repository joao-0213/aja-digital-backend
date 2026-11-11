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

    @Test
    fun `rejects public writes without an origin`() {
        val request = MockHttpServletRequest("POST", "/api/v1/auth/register")
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        assertEquals(403, response.status)
        assertNull(chain.request)
    }

    @Test
    fun `rejects null malformed and duplicate origins on writes`() {
        listOf("null", "not-an-origin").forEach { origin ->
            val request = MockHttpServletRequest("POST", "/api/v1/auth/register").apply { addHeader("Origin", origin) }
            val response = MockHttpServletResponse()
            filter.doFilter(request, response, MockFilterChain())
            assertEquals(403, response.status)
        }
        val duplicate = MockHttpServletRequest("POST", "/api/v1/auth/register").apply {
            addHeader("Origin", "http://127.0.0.1:3000")
            addHeader("Origin", "http://127.0.0.1:3000")
        }
        val duplicateResponse = MockHttpServletResponse()
        val duplicateChain = MockFilterChain()
        filter.doFilter(duplicate, duplicateResponse, duplicateChain)
        assertEquals(403, duplicateResponse.status)
        assertNull(duplicateChain.request)
    }

    @Test
    fun `allows an exact configured origin on writes`() {
        val request = MockHttpServletRequest("POST", "/api/v1/auth/register").apply {
            addHeader("Origin", "http://127.0.0.1:3000")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)
        assertNotNull(chain.request)
        assertEquals("http://127.0.0.1:3000", response.getHeader("Access-Control-Allow-Origin"))
    }

    @Test
    fun `answers allowed browser preflight for json login`() {
        val request = MockHttpServletRequest("OPTIONS", "/api/v1/auth/login").apply {
            addHeader("Origin", "http://127.0.0.1:3000")
            addHeader("Access-Control-Request-Method", "POST")
            addHeader("Access-Control-Request-Headers", "content-type")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(request, response, chain)

        assertEquals(204, response.status)
        assertEquals("http://127.0.0.1:3000", response.getHeader("Access-Control-Allow-Origin"))
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"))
        assertEquals("POST", response.getHeader("Access-Control-Allow-Methods"))
        assertEquals("content-type", response.getHeader("Access-Control-Allow-Headers"))
        assertNull(chain.request)
    }

    @Test
    fun `rejects unsupported preflight methods and headers`() {
        listOf("authorization", "x-unknown-header").forEach { requestedHeader ->
            val request = MockHttpServletRequest("OPTIONS", "/api/v1/auth/login").apply {
                addHeader("Origin", "http://127.0.0.1:3000")
                addHeader("Access-Control-Request-Method", "POST")
                addHeader("Access-Control-Request-Headers", requestedHeader)
            }
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(request, response, chain)
            assertEquals(403, response.status)
            assertNull(chain.request)
        }

        val unsupportedMethod = MockHttpServletRequest("OPTIONS", "/api/v1/auth/login").apply {
            addHeader("Origin", "http://127.0.0.1:3000")
            addHeader("Access-Control-Request-Method", "TRACE")
        }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        filter.doFilter(unsupportedMethod, response, chain)
        assertEquals(403, response.status)
        assertNull(chain.request)
    }
}
