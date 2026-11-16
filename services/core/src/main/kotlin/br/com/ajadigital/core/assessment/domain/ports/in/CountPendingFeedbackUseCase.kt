package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CountPendingFeedbackUseCase {
    fun pendingFeedbackCount(actor: AcademicActor): Int
}
