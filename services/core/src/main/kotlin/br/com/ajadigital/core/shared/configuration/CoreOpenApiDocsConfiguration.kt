package br.com.ajadigital.core.shared.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.core.util.Json31
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import tools.jackson.databind.node.ObjectNode

@Configuration
@Profile("docs")
class CoreOpenApiDocsConfiguration {
    @Bean
    fun corePublicOpenApiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("core-public")
        .pathsToMatch("/api/v1/**")
        .addOpenApiCustomizer(CoreNormativeOpenApiCustomizer())
        .build()
}

private class CoreNormativeOpenApiCustomizer : OpenApiCustomizer {
    override fun customise(openApi: OpenAPI) {
        val mapper = Json31.mapper()
        val root = mapper.readTree(ClassPathResource("META-INF/aja/openapi.json").inputStream) as ObjectNode
        val paths = root.get("paths") as ObjectNode
        paths.remove(paths.propertyNames().filter { it == "/questions" || it.startsWith("/questions/") })

        val components = root.get("components") as ObjectNode
        val reachable = mutableSetOf<Pair<String, String>>()
        val pending = ArrayDeque<Pair<String, String>>()
        fun collectReferences(node: tools.jackson.databind.JsonNode) {
            when {
                node.isObject -> node.properties().forEach { (key, value) ->
                    if (key == "\$ref") {
                        val parts = value.asText().removePrefix("#/components/").split('/', limit = 2)
                        if (parts.size == 2 && reachable.add(parts[0] to parts[1])) pending.add(parts[0] to parts[1])
                    } else collectReferences(value)
                }
                node.isArray -> node.forEach(::collectReferences)
            }
        }
        collectReferences(paths)
        fun collectSecurity(node: tools.jackson.databind.JsonNode?) {
            if (node?.isArray == true) node.forEach { requirement ->
                requirement.properties().forEach { (name, _) -> reachable.add("securitySchemes" to name) }
            }
        }
        collectSecurity(root.get("security"))
        paths.properties().forEach { (_, pathItem) -> pathItem.properties().forEach { (_, operation) -> collectSecurity(operation.get("security")) } }
        while (pending.isNotEmpty()) {
            val (section, name) = pending.removeFirst()
            components.get(section)?.get(name)?.let(::collectReferences)
        }
        components.properties().forEach { (section, definitions) ->
            if (definitions is ObjectNode) definitions.remove(definitions.propertyNames().filter { section to it !in reachable })
        }

        val servers = root.putArray("servers")
        servers.addObject().put("url", "/api/v1")
        val contract = mapper.treeToValue(root, OpenAPI::class.java)
        openApi.openapi = contract.openapi
        openApi.info = contract.info
        openApi.servers = contract.servers
        openApi.security = contract.security
        openApi.paths = contract.paths
        openApi.components = contract.components
        openApi.tags = contract.tags
        openApi.externalDocs = contract.externalDocs
    }
}
