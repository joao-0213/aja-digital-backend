package br.com.ajadigital.core.adapter.out.messaging

import java.util.UUID


@org.springframework.web.bind.annotation.RestController
@org.springframework.web.bind.annotation.RequestMapping("/internal/ops/outbox")
class OutboxOperationsController(
    private val redrive: OutboxRedrive,
    @org.springframework.beans.factory.annotation.Value("\${core.outbox.operator-token}") private val operatorToken: String,
) {
    init {
        require(operatorToken.isNotBlank()) { "Core outbox operator token must not be blank" }
    }

    @org.springframework.web.bind.annotation.PostMapping("/{id}/redrive")
    fun redrive(
        @org.springframework.web.bind.annotation.PathVariable id: String,
        @org.springframework.web.bind.annotation.RequestHeader("X-AJA-Operator-Token", required = false) suppliedToken: String?,
    ): org.springframework.http.ResponseEntity<Void> {
        if (suppliedToken.isNullOrBlank() || !java.security.MessageDigest.isEqual(operatorToken.toByteArray(), suppliedToken.toByteArray())) {
            return org.springframework.http.ResponseEntity.status(401).build()
        }
        if (!runCatching { UUID.fromString(id) }.isSuccess || !redrive.redrive(id)) return org.springframework.http.ResponseEntity.notFound().build()
        return org.springframework.http.ResponseEntity.accepted().build()
    }
}
