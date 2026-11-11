package br.com.ajadigital.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ArchitectureTests {
    @Test
    fun `application and inbound adapters stay behind ports`() {
        val root = Path.of("src/main/kotlin/br/com/ajadigital/core")
        val application = Files.walk(root.resolve("application")).use { files ->
            files.filter { it.toString().endsWith(".kt") }.map { Files.readString(it) }.toList()
        }
        assertTrue(application.isNotEmpty())
        assertTrue(application.none { it.contains("org.springframework") || it.contains("javax.sql") || it.contains("com.rabbitmq") })
        val controllers = Files.walk(root).use { files ->
            files.filter { it.fileName.toString().endsWith("Controller.kt") }.map { Files.readString(it) }.toList()
        }
        assertFalse(controllers.any { it.contains("javax.sql.DataSource") || it.contains("ConnectionFactory") })
        assertTrue(Files.exists(root.resolve("ports/out/HealthProbe.kt")))
    }
}
