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

    @Test
    fun `controllers, upload filter, and dashboard use case depend on incoming ports instead of concrete use cases`() {
        val root = Path.of("src/main/kotlin/br/com/ajadigital/core")
        val callers = Files.walk(root).use { files ->
            files.filter {
                val norm = it.normalized()
                (norm.endsWith("Controller.kt") && !norm.endsWith("OutboxOperationsController.kt")) ||
                    norm.endsWith("/FileUploadSessionFilter.kt") ||
                    norm.endsWith("/GetDashboard.kt")
            }.map { it.fileName.toString() to Files.readString(it) }.toList()
        }
        assertTrue(callers.isNotEmpty())
        val offenders = callers.filter { (_, src) ->
            src.lines().any { it.contains(".domain.usecases.") && !it.trim().endsWith(".toResponse") }
        }.map { it.first }
        assertTrue(offenders.isEmpty(), "Expected incoming port imports only, found concrete use-case imports in: $offenders")
    }

    @Test
    fun `controllers do not declare @Transactional and CoreApplication delegates bean wiring to feature configurations`() {
        val root = Path.of("src/main/kotlin/br/com/ajadigital/core")
        val controllers = Files.walk(root).use { files ->
            files.filter { it.fileName.toString().endsWith("Controller.kt") }
                .map { it.fileName.toString() to Files.readString(it) }.toList()
        }
        val txControllers = controllers.filter { (_, src) -> src.contains("@Transactional") }.map { it.first }
        assertTrue(txControllers.isEmpty(), "Controllers must not declare @Transactional: $txControllers")

        val coreApp = Files.readString(root.resolve("CoreApplication.kt"))
        assertFalse(coreApp.contains("@Bean"), "CoreApplication.kt must not declare @Bean methods")

        assertTrue(Files.exists(root.resolve("shared/domain/ports/out/TransactionRunner.kt")))
        assertTrue(Files.exists(root.resolve("shared/adapters/out/transaction/SpringTransactionRunner.kt")))
    }

    private fun Path.normalized(): String = toString().replace('\\', '/')
}
