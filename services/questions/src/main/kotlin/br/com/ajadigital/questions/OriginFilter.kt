package br.com.ajadigital.questions

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class OriginFilter(@Value("\${API_ALLOWED_ORIGINS:}") allowedOrigins: String) : OncePerRequestFilter() {
    private val allowed = allowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty).toSet()

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.requestURI.startsWith("/internal/v1/")) return chain.doFilter(request, response)
        val origins = java.util.Collections.list(request.getHeaders("Origin"))
        val unsafe = request.method !in setOf("GET", "HEAD", "OPTIONS")
        if (origins.isEmpty() && !unsafe) return chain.doFilter(request, response)
        val origin = origins.singleOrNull()
        if (origin == null || origin !in allowed) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"error":{"code":"FORBIDDEN","message":"Origin is not allowed","requestId":"${UUID.randomUUID()}"}}""")
            return
        }
        response.setHeader("Access-Control-Allow-Origin", origin)
        response.setHeader("Access-Control-Allow-Credentials", "true")
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition, X-Content-Type-Options")
        response.addHeader("Vary", "Origin")
        if (request.method == "OPTIONS" && request.getHeader("Access-Control-Request-Method") != null) {
            val method = request.getHeader("Access-Control-Request-Method")?.uppercase()
            val headers = request.getHeader("Access-Control-Request-Headers")
                ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            if (method !in setOf("GET", "POST") || headers.any { it.lowercase() != "content-type" }) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = "application/json"
                response.writer.write("""{"error":{"code":"FORBIDDEN","message":"Origin is not allowed","requestId":"${UUID.randomUUID()}"}}""")
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
}
