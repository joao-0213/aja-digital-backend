package br.com.ajadigital.core.assessment.domain.policies

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class DeadlinePolicyTests {
    @Test
    fun `allows when deadline is null or allowLate is true`() {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        val past = Instant.parse("2026-11-15T12:00:00Z")
        assertTrue(DeadlinePolicy.isBeforeDeadline(null, allowLate = false, now = now))
        assertTrue(DeadlinePolicy.isBeforeDeadline(past, allowLate = true, now = now))
    }

    @Test
    fun `enforces strict before deadline when allowLate is false`() {
        val now = Instant.parse("2026-11-16T12:00:00Z")
        val future = Instant.parse("2026-11-16T12:00:01Z")
        val past = Instant.parse("2026-11-16T11:59:59Z")
        assertTrue(DeadlinePolicy.isBeforeDeadline(future, allowLate = false, now = now))
        assertFalse(DeadlinePolicy.isBeforeDeadline(now, allowLate = false, now = now))
        assertFalse(DeadlinePolicy.isBeforeDeadline(past, allowLate = false, now = now))
    }
}
