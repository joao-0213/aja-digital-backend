package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CheckPracticeBlockedUseCase {
    fun practiceBlocked(actor: AcademicActor, questionId: String): Boolean
}
