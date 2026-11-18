package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.*

import br.com.ajadigital.core.academic.application.models.CountMyPendingEnrollmentsDto

interface CountMyPendingEnrollmentsInputPort {
    fun myPendingEnrollmentCount(request: CountMyPendingEnrollmentsDto): Int
}
