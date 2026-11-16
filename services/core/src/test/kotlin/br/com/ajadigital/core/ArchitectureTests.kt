package br.com.ajadigital.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ArchitectureTests {
    @Test
    fun `feature domains stay free of framework and transport dependencies`() {
        val root = Path.of("src/main/kotlin/br/com/ajadigital/core")
        val domain = Files.walk(root).use { files ->
            files.filter { it.toString().endsWith(".kt") && it.normalized().contains("/domain/") }
                .map { Files.readString(it) }.toList()
        }
        assertTrue(domain.isNotEmpty())
        assertTrue(
            domain.none {
                it.contains("org.springframework") || it.contains("javax.sql") ||
                    it.contains("com.rabbitmq") || it.contains("jakarta.")
            },
        )
    }

    @Test
    fun `inbound adapters do not reach persistence plumbing`() {
        val root = Path.of("src/main/kotlin/br/com/ajadigital/core")
        val controllers = Files.walk(root).use { files ->
            files.filter { it.fileName.toString().endsWith("Controller.kt") }.map { Files.readString(it) }.toList()
        }
        assertTrue(controllers.isNotEmpty())
        assertFalse(controllers.any { it.contains("javax.sql.DataSource") || it.contains("ConnectionFactory") })
    }

    @Test
    fun `health probe port lives in its feature domain`() {
        val root = Path.of("src/main/kotlin/br/com/ajadigital/core")
        assertTrue(Files.exists(root.resolve("health/domain/ports/out/HealthProbe.kt")))
    }

    private fun Path.normalized(): String = toString().replace('\\', '/')
}
