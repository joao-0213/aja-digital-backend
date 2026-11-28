package br.com.ajadigital.core.shared.adapters.`in`.web.errors

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.util.UUID

/** Shared wire mappings for feature-owned failures with identical HTTP bodies. */
object FeatureHttpErrors {
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody("FORBIDDEN", "This action is not allowed"))
    fun notFound() = ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody("NOT_FOUND", "Resource was not found"))
    fun idempotencyConflict() = ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorBody("IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request"))

    fun conflict(currentRevision: Int?): ResponseEntity<Any> {
        val body = mutableMapOf<String, Any?>("code" to "REVISION_CONFLICT", "message" to "Resource changed or conflicts with current state", "requestId" to UUID.randomUUID().toString())
        currentRevision?.let { body["details"] = mapOf("currentRevision" to it) }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to body))
    }
}
