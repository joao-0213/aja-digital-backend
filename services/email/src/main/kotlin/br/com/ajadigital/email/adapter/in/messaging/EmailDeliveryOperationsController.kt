package br.com.ajadigital.email.adapter.`in`.messaging

import br.com.ajadigital.email.application.delivery.RedriveDeadEmail
import br.com.ajadigital.email.application.delivery.RedriveResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest
import java.util.UUID

@RestController
@RequestMapping("/internal/ops/delivery")
class EmailDeliveryOperationsController(
    private val redrive: RedriveDeadEmail,
    @Value("\${email.delivery.operator-token}") private val operatorToken: String,
) {
    init {
        require(operatorToken.isNotBlank()) { "Email delivery operator token must not be blank" }
    }

    @PostMapping("/{eventId}/redrive")
    fun redrive(
        @PathVariable eventId: String,
        @RequestHeader("X-AJA-Operator-Token", required = false) suppliedToken: String?,
    ): ResponseEntity<Void> {
        if (suppliedToken.isNullOrBlank() || !MessageDigest.isEqual(operatorToken.toByteArray(), suppliedToken.toByteArray())) {
            return ResponseEntity.status(401).build()
        }
        if (runCatching { UUID.fromString(eventId) }.isFailure) return ResponseEntity.notFound().build()
        return when (redrive.redrive(eventId)) {
            RedriveResult.ACCEPTED, RedriveResult.IN_PROGRESS -> ResponseEntity.accepted().build()
            RedriveResult.NOT_ELIGIBLE -> ResponseEntity.notFound().build()
        }
    }
}
