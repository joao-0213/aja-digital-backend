package br.com.ajadigital.core.shared.adapters.`in`.web.filters

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI
import java.util.Collections
import java.util.UUID

@Component
class OriginFilter(@Value("\${API_ALLOWED_ORIGINS:}") allowedOrigins: String) : OncePerRequestFilter() {
    private val allowed = allowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.requestURI.startsWith("/internal/v1/")) return chain.doFilter(request, response)
        val origins = Collections.list(request.getHeaders("Origin"))
        val unsafe = request.method !in setOf("GET", "HEAD", "OPTIONS")
        if (origins.isEmpty() && !unsafe) return chain.doFilter(request, response)
        val origin = origins.singleOrNull()
        if (origin == null || !isOrigin(origin) || origin !in allowed) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"error":{"code":"FORBIDDEN","message":"Origin is not allowed","requestId":"${UUID.randomUUID()}"}}""")
            return
        }
        response.setHeader("Access-Control-Allow-Origin", origin)
        response.setHeader("Access-Control-Allow-Credentials", "true")
        response.addHeader("Vary", "Origin")
        if (request.method == "OPTIONS" && request.getHeader("Access-Control-Request-Method") != null) {
            val methods = Collections.list(request.getHeaders("Access-Control-Request-Method"))
            val method = methods.singleOrNull()?.uppercase()
            val requestHeaders = Collections.list(request.getHeaders("Access-Control-Request-Headers"))
            val headers = requestHeaders.flatMap { it.split(',') }.map(String::trim).filter(String::isNotEmpty)
            if (method !in setOf("POST", "PATCH") || headers.any { it.lowercase() != "content-type" }) {
                reject(response)
                return
            }
            response.setHeader("Access-Control-Allow-Methods", method)
            if (headers.isNotEmpty()) response.setHeader("Access-Control-Allow-Headers", "content-type")
            response.addHeader("Vary", "Access-Control-Request-Method")
            response.addHeader("Vary", "Access-Control-Request-Headers")
            response.status = HttpServletResponse.SC_NO_CONTENT
            return
        }
        chain.doFilter(request, response)
    }

    private fun reject(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json"
        response.writer.write("""{"error":{"code":"FORBIDDEN","message":"Origin is not allowed","requestId":"${UUID.randomUUID()}"}}""")
    }

    private fun isOrigin(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") && uri.rawAuthority != null && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)
}
