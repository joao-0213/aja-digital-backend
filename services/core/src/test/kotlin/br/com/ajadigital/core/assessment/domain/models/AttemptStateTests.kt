package br.com.ajadigital.core.assessment.domain.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class AttemptStateTests {
    @Test
    fun `maps missing submission timestamp to in progress and timestamp to submitted`() {
        assertEquals(AttemptState.InProgress, AttemptState.from(null))
        assertEquals(
            AttemptState.Submitted(Instant.parse("2026-11-16T12:00:00Z")),
            AttemptState.from("2026-11-16T12:00:00Z"),
        )
    }
}
