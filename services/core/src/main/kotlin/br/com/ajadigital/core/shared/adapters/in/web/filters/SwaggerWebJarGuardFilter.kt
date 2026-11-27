package br.com.ajadigital.core.shared.adapters.`in`.web.filters

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class SwaggerWebJarGuardFilter(private val environment: Environment) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val path = request.requestURI.removePrefix(request.contextPath)
        val docsEnabled = environment.activeProfiles.contains("docs")
        val disabledDocsAsset = !docsEnabled && path.startsWith("/swagger-ui/")
        if (path.startsWith("/webjars/swagger-ui/") || disabledDocsAsset) response.sendError(HttpServletResponse.SC_NOT_FOUND)
        else chain.doFilter(request, response)
    }
}
