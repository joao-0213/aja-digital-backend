package br.com.ajadigital.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import io.swagger.v3.core.util.Yaml31
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ActiveProfiles
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

@ActiveProfiles("docs")
class CoreDocsProfileIntegrationTests : CoreIntegrationTestBase() {
    @Autowired @Qualifier("requestMappingHandlerMapping") private lateinit var handlerMapping: RequestMappingHandlerMapping

    @Test
    fun docsProfileServesOnlyTheNormativeCoreProjectionAndRegisteredRoutes() {
        val json = request("/v3/api-docs/core-public")
        assertEquals(200, json.statusCode(), json.body())
        val mapper = tools.jackson.databind.json.JsonMapper.builder().build()
        val actual = mapper.readTree(json.body())
        val sourcePath = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .map { it.resolve("docs/http/openapi.json") }.first { Files.isRegularFile(it) }
        val normative = mapper.readTree(Files.readString(sourcePath))
        val expectedPaths = mapper.createObjectNode()
        normative.get("paths").properties().filterNot { it.key == "/questions" || it.key.startsWith("/questions/") }
            .forEach { (path, value) -> expectedPaths.set(path, value) }
        assertEquals(normalizeRequired(expectedPaths), normalizeRequired(actual.get("paths")))
        assertEquals("3.1.0", actual.get("openapi").asText())
        assertEquals("/api/v1", actual.at("/servers/0/url").asText())
        assertEquals("apiKey", actual.at("/components/securitySchemes/sessionCookie/type").asText())
        assertEquals("aja_session", actual.at("/components/securitySchemes/sessionCookie/name").asText())
        assertEquals("cookie", actual.at("/components/securitySchemes/sessionCookie/in").asText())
        assertEquals(normative.get("security"), actual.get("security"))
        assertFalse(json.body().contains("127.0.0.1:3002"))
        assertFalse(json.body().contains("internal/v1"))
        assertFalse(json.body().contains("operator-token"))
        assertEquals(404, request("/v3/api-docs").statusCode())
        assertEquals(404, request("/v3/api-docs.yaml").statusCode())
        val yaml = request("/v3/api-docs.yaml/core-public")
        assertEquals(200, yaml.statusCode(), yaml.body())
        val yamlDocument = Yaml31.mapper().readTree(yaml.body())
        assertEquals("3.1.0", yamlDocument.get("openapi").asText())
        assertEquals(normalizeRequired(actual.get("paths")), normalizeRequired(yamlDocument.get("paths")))
        assertEquals(normalizeRequired(actual.get("components")), normalizeRequired(yamlDocument.get("components")))
        actual.get("components").properties().forEach { (section, definitions) -> definitions.properties().forEach { (name, value) ->
            assertEquals(normalizeRequired(normative.at("/components/$section/$name")), normalizeRequired(value), "$section/$name")
        } }
        assertAllReferencesResolve(actual)
        assertEquals(404, request("/docs/http/openapi.json").statusCode())
        assertEquals(404, request("/META-INF/aja/openapi.json").statusCode())
        assertEquals(200, request("/swagger-ui/swagger-ui-bundle.js").statusCode())
        assertEquals(404, request("/webjars/swagger-ui/5.35.1/index.html").statusCode())
        assertEquals(404, request("/webjars/swagger-ui/5.35.1/swagger-initializer.js").statusCode())
        assertEquals(404, request("/v3/api-docs/core-public.yaml").statusCode())

        val documented = expectedPaths.properties().flatMap { (path, methods) ->
            methods.properties().filter { it.key in setOf("get", "post", "patch", "put", "delete", "options", "head", "trace") }
                .map { "${it.key.uppercase()} /api/v1$path" }
        }.toSet()
        val registered = handlerMapping.handlerMethods.keys.flatMap(::routeOperations)
            .filter { it.substringAfter(' ').startsWith("/api/v1/") }.toSet()
        assertEquals(documented, registered)
    }

    @Test
    fun swaggerUiUsesTheConfiguredSpecAndExistingHttpOnlySession() {
        val ui = request("/swagger-ui.html")
        assertTrue(ui.statusCode() in 200..399, ui.body())
        val index = request("/swagger-ui/index.html")
        assertEquals(200, index.statusCode(), index.body())
        val initializer = request("/swagger-ui/swagger-initializer.js")
        assertEquals(200, initializer.statusCode(), initializer.body())
        assertFalse(initializer.body().contains("petstore.swagger.io"))
        val swaggerConfig = request("/v3/api-docs/swagger-config")
        assertEquals(200, swaggerConfig.statusCode(), swaggerConfig.body())
        val config = tools.jackson.databind.json.JsonMapper.builder().build().readTree(swaggerConfig.body())
        val configuredUrl = config.get("urls")?.firstOrNull()?.get("url")?.asText() ?: config.get("url")?.asText()
        assertEquals("/v3/api-docs/core-public", configuredUrl)
        assertEquals(200, request(checkNotNull(configuredUrl)).statusCode())
        assertTrue(swaggerConfig.body().contains("\"validatorUrl\":\"none\""))
        assertTrue(swaggerConfig.body().contains("\"withCredentials\":true"))
        assertFalse(swaggerConfig.body().contains("validator.swagger.io"))
        assertFalse(swaggerConfig.body().contains("petstore.swagger.io"))

        val email = "docs-${UUID.randomUUID()}@example.test"
        assertEquals(201, liveHttp("POST", "/auth/register", null, """{"name":"Docs Student","email":"$email","password":"A long test password"}""").statusCode())
        val login = liveHttp("POST", "/auth/login", null, """{"email":"$email","password":"A long test password"}""")
        assertEquals(200, login.statusCode(), login.body())
        val setCookie = login.headers().firstValue("Set-Cookie").orElseThrow()
        assertTrue(setCookie.contains("HttpOnly"))
        val cookie = setCookie.substringAfter("aja_session=").substringBefore(';')
        assertEquals(200, liveHttp("GET", "/me", cookie).statusCode())
    }

    @Test
    fun packagedBootJarLoadsTheNormativeSpecInTheDocsProfile() {
        var child: CoreChild? = null
        try {
            val started = startCoreProcess(profiles = "docs")
            child = started
            val response = request(started.port, "/v3/api-docs/core-public")
            assertEquals(200, response.statusCode(), response.body())
            val document = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response.body())
            assertEquals(72, document.get("paths").size())
            assertEquals("3.1.0", document.get("openapi").asText())
            assertEquals("/api/v1", document.at("/servers/0/url").asText())
            assertTrue(document.at("/components/securitySchemes/sessionCookie/name").asText() == "aja_session")
        } finally {
            child?.let(::stopCoreProcess)
        }
    }

    private fun request(path: String): HttpResponse<String> = request(checkNotNull(webContext.webServer).port, path)

    private fun request(port: Int, path: String): HttpResponse<String> = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun routeOperations(info: RequestMappingInfo): List<String> {
        val paths = info.pathPatternsCondition?.patterns?.map { it.patternString } ?: info.patternValues
        return paths.flatMap { path -> info.methodsCondition.methods.map { "${it.name} $path" } }
    }

    private fun normalizeRequired(node: JsonNode, field: String? = null): JsonNode = when {
        node is ObjectNode -> node.also { copy ->
            copy.properties().toList().forEach { (name, value) ->
                if (name == "required" && value.isArray && value.isEmpty) copy.remove(name)
                else copy.set(name, normalizeRequired(value, name))
            }
        }
        node is ArrayNode -> {
            val values = node.elements().asSequence().map { normalizeRequired(it) }.toList()
            val result = tools.jackson.databind.json.JsonMapper.builder().build().createArrayNode()
            (if (field == "required") values.sortedBy { it.asString() } else values)
                .forEach(result::add)
            result
        }
        else -> node
    }

    private fun assertAllReferencesResolve(document: JsonNode) {
        val components = document.get("components")
        val seen = mutableSetOf<String>()
        fun visit(node: JsonNode) {
            when {
                node.isObject -> node.properties().forEach { (key, value) ->
                    if (key == "\$ref") {
                        val reference = value.asText()
                        assertTrue(reference.startsWith("#/components/"), reference)
                        val parts = reference.removePrefix("#/components/").split('/', limit = 2)
                        assertEquals(2, parts.size, reference)
                        assertTrue(components.get(parts[0])?.has(parts[1]) == true, reference)
                        if (seen.add(reference)) visit(components.get(parts[0]).get(parts[1]))
                    } else visit(value)
                }
                node.isArray -> node.forEach(::visit)
            }
        }
        visit(document.get("paths"))
        visit(document.get("security"))
    }
}
