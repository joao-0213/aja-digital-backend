package br.com.ajadigital.core.materials.adapters.`in`.filters

import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.identity.domain.exceptions.SessionRequired
import br.com.ajadigital.core.materials.application.models.QuestionServiceCredential
import br.com.ajadigital.core.materials.application.ports.out.QuestionServiceAccessOutputPort
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID


@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class FileUploadSessionFilter(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val serviceAccess: QuestionServiceAccessOutputPort,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        if (request.method == "POST" && request.requestURI.startsWith("/internal/v1/question-files/")) {
            val supplied = QuestionServiceCredential(request.getHeader("X-AJA-Service-Token"))
            if (!serviceAccess.allows(supplied)) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = "application/json"
                response.writer.write("""{"error":{"code":"FORBIDDEN","message":"Service credential is invalid","requestId":"${UUID.randomUUID()}"}}""")
                return
            }
            return chain.doFilter(request, response)
        }
        if (request.method != "POST" || request.requestURI != "/api/v1/files") return chain.doFilter(request, response)
        val cookie = request.cookies?.firstOrNull { it.name == "aja_session" }?.value
        try { auth.resolve(cookie) } catch (_: SessionRequired) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error":{"code":"SESSION_REQUIRED","message":"A valid session is required","requestId":"${UUID.randomUUID()}"}}""")
            return
        }
        chain.doFilter(request, response)
    }
}
