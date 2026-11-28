package br.com.ajadigital.core.health.adapters.`in`.controllers

import br.com.ajadigital.core.health.application.ports.`in`.CheckServiceHealthInputPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class HealthController(
    private val checkServiceHealth: CheckServiceHealthInputPort,
    @Value("\${app.version:1}") private val version: String,
) {
    @GetMapping("/health")
    fun health(): ResponseEntity<Any> = if (checkServiceHealth().available) {
        ResponseEntity.ok(mapOf("status" to "ok", "version" to version))
    } else {
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            mapOf(
                "error" to mapOf(
                    "code" to "API_UNAVAILABLE",
                    "message" to "Service dependencies are unavailable",
                    "requestId" to UUID.randomUUID().toString(),
                ),
            ),
        )
    }
}
