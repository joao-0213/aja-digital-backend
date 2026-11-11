package br.com.ajadigital.core

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
        val origin = request.getHeader("Origin")
        if (origin == null) return chain.doFilter(request, response)
        if (origin !in allowed) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json"
            response.writer.write("""{"error":{"code":"FORBIDDEN","message":"Origin is not allowed","requestId":"${UUID.randomUUID()}"}}""")
            return
        }
        response.setHeader("Access-Control-Allow-Origin", origin)
        response.setHeader("Access-Control-Allow-Credentials", "true")
        response.addHeader("Vary", "Origin")
        chain.doFilter(request, response)
    }
}
