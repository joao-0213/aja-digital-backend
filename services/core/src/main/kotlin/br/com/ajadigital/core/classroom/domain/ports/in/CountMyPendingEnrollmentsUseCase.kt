package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CountMyPendingEnrollmentsUseCase {
    fun myPendingEnrollmentCount(actor: AcademicActor): Int
}
