package br.com.ajadigital.core.assessment.domain.policies

import java.time.Instant

object DeadlinePolicy {
    fun isBeforeDeadline(
        deadline: Instant?,
        allowLate: Boolean,
        now: Instant,
    ): Boolean = deadline == null || allowLate || now.isBefore(deadline)
}
