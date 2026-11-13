package br.com.ajadigital.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.get
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.pattern.PathPatternParser
import org.springframework.http.server.PathContainer
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.concurrent.ConcurrentHashMap


class CoreOpenApiCoverageFilter : OncePerRequestFilter() {
    data class DenialProbe(val id: String, val method: String, val target: String, val expectedStatus: Int, val body: String?, val contentType: String, val idempotencyKey: Boolean)
    private data class Operation(val id: String, val method: String, val path: String, val target: String, val pattern: org.springframework.web.util.pattern.PathPattern, val responseCodes: Set<String>, val requiresSession: Boolean, val body: String?, val contentType: String)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        try { chain.doFilter(request, response) } finally {
            if (request !is MockHttpServletRequest) {
                val operation = operations.firstOrNull { it.method == request.method && it.pattern.matches(PathContainer.parsePath(request.requestURI)) }
                operation?.let { observed.computeIfAbsent(it) { ConcurrentHashMap.newKeySet() }.add(response.status) }
            }
        }
    }

    fun expectedDenials(): List<DenialProbe> = operations.map { operation ->
        val status = when {
            operation.id == "logout" -> 204
            operation.requiresSession -> 401
            operation.id == "health" -> 403
            else -> 422
        }
        assertTrue(status.toString() in operation.responseCodes, "OpenAPI does not declare the expected denial $status for ${operation.method} ${operation.path} (${operation.id})")
        val multipartBoundary = "B16ProbeBoundary"
        val multipart = "--$multipartBoundary\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\nmaterial\r\n--$multipartBoundary\r\nContent-Disposition: form-data; name=\"parentId\"\r\n\r\ns1\r\n--$multipartBoundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"probe.txt\"\r\nContent-Type: text/plain\r\n\r\nprobe\r\n--$multipartBoundary--\r\n"
        val body = when {
            status == 204 || operation.id == "health" -> null
            status == 401 -> operation.body ?: if (operation.contentType == "multipart/form-data") multipart else null
            else -> "{}"
        }
        val contentType = if (operation.contentType == "multipart/form-data") "multipart/form-data; boundary=$multipartBoundary" else "application/json"
        DenialProbe(operation.id, operation.method, operation.target, status, body, contentType, status == 401 && body != null)
    }

    fun recordProbeResponse(method: String, target: String, status: Int) {
        val path = URI("http://localhost/api/v1$target").path
        val operation = operations.firstOrNull { it.method == method && it.pattern.matches(PathContainer.parsePath(path)) }
        assertTrue(operation != null, "Probe did not match an OpenAPI operation: $method $target")
        observed.computeIfAbsent(checkNotNull(operation)) { ConcurrentHashMap.newKeySet() }.add(status)
    }

    companion object {
        // Observations and the expected operation set are JVM-wide so the aggregate check still
        // sees every feature test class after CoreApplicationTests was split per feature.
        private val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        private val document by lazy {
            val specPath = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .map { it.resolve("docs/http/openapi.json") }.first { Files.isRegularFile(it) }
            mapper.readTree(Files.readString(specPath))
        }
        private val operations by lazy {
            document.get("paths").properties().asSequence().flatMap { (path, methods) ->
                if (path.startsWith("/questions")) emptySequence() else methods.properties().asSequence()
                    .filter { it.key in setOf("get", "post", "patch", "put", "delete", "options", "head", "trace") }
                    .map { (method, operation) ->
                        val security = operation.get("security") ?: document.get("security")
                        val parameters = sequenceOf(methods.get("parameters"), operation.get("parameters"))
                            .filterNotNull().flatMap { it.iterator().asSequence() }
                            .map(::resolveParameter)
                            .toList()
                        val query = parameters
                            .filter { it.get("in")?.asText() == "query" }
                            .joinToString("&") { parameter ->
                                val name = java.net.URLEncoder.encode(parameter.get("name").asText(), Charsets.UTF_8)
                                val value = java.net.URLEncoder.encode(parameterExample(parameter), Charsets.UTF_8)
                                "$name=$value"
                            }
                        val pathValues = parameters.filter { it.get("in")?.asText() == "path" }
                            .associate { it.get("name").asText() to parameterExample(it, uuidFallback = true) }
                        val concretePath = Regex("\\{([^}]+)}").replace(path) { match ->
                            pathValues[match.groupValues[1]] ?: "00000000-0000-4000-8000-000000000000"
                        }
                        val target = concretePath + if (query.isEmpty()) "" else "?$query"
                        val requestContent = operation.get("requestBody")?.get("content")
                        val mediaType = requestContent?.properties()?.firstOrNull()?.key ?: "application/json"
                        val body = if (mediaType == "application/json") {
                            val content = requestContent?.get(mediaType)
                            val example = content?.get("example") ?: content?.get("examples")?.properties()?.firstOrNull()?.value?.get("value")
                            example?.toString()
                        } else null
                        Operation(operation.get("operationId").asText(), method.uppercase(), path, target, PathPatternParser().parse("/api/v1$path"), operation.get("responses").properties().map { it.key }.toSet(), security != null && !security.isEmpty, body, mediaType)
                    }
            }.toList().sortedWith(compareBy<Operation> { it.path.count { char -> char == '{' } }.thenByDescending { it.path.length })
        }
        private val observed = ConcurrentHashMap<Operation, MutableSet<Int>>()

        private fun resolveParameter(parameter: tools.jackson.databind.JsonNode): tools.jackson.databind.JsonNode {
            val reference = parameter.get("\$ref")?.asText() ?: return parameter
            return document.at(reference.removePrefix("#"))
        }

        private fun parameterExample(parameter: tools.jackson.databind.JsonNode, uuidFallback: Boolean = false): String {
            val schema = parameter.get("schema")
            val example = parameter.get("example") ?: schema?.get("example") ?: schema?.get("enum")?.firstOrNull()
            if (example != null && !example.isNull) return if (example.isArray) example.firstOrNull()?.asText().orEmpty() else example.asText()
            return when (schema?.get("format")?.asText()) {
                "date" -> "2026-11-02"
                "date-time" -> "2026-11-02T12:00:00Z"
                "uuid" -> "00000000-0000-4000-8000-000000000000"
                else -> when (schema?.get("type")?.asText()) {
                    "integer", "number" -> "1"
                    "boolean" -> "true"
                    else -> if (uuidFallback) "00000000-0000-4000-8000-000000000000" else "probe"
                }
            }
        }

        @JvmStatic
        fun assertEveryOperationHasSuccessAndExpectedDenial() {
            val missing = operations.filter { operation -> observed[operation].orEmpty().none { it in 200..299 } }
            assertTrue(missing.isEmpty(), "OpenAPI operations without a successful HTTP request: ${missing.joinToString { "${it.method} ${it.path} (${it.id}) status=${observed[it]}" }}")
            val missingDenial = operations.filter { operation ->
                val expected = when { operation.id == "logout" -> 204; operation.requiresSession -> 401; operation.id == "health" -> 403; else -> 422 }
                expected !in observed[operation].orEmpty()
            }
            assertTrue(missingDenial.isEmpty(), "OpenAPI operations without their expected HTTP denial: ${missingDenial.joinToString { "${it.method} ${it.path} (${it.id}) status=${observed[it]}" }}")
        }
    }
}
