package br.com.ajadigital.core.assessment.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CountPendingStudentAssessmentsUseCase {
    fun pendingStudentCount(actor: AcademicActor): Int
}
