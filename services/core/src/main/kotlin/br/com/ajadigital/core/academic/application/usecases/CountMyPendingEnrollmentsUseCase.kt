package br.com.ajadigital.core.academic.application.usecases

import br.com.ajadigital.core.academic.application.models.CountMyPendingEnrollmentsDto
import br.com.ajadigital.core.academic.application.models.CountPendingEnrollmentsForStudentDto
import br.com.ajadigital.core.academic.application.ports.`in`.CountMyPendingEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class CountMyPendingEnrollmentsUseCase(private val store: AcademicStoreOutputPort) : CountMyPendingEnrollmentsInputPort {
    override fun myPendingEnrollmentCount(request: CountMyPendingEnrollmentsDto): Int {
        val (actor) = request
        if (actor.role != "student" || !actor.active || actor.emailConfirmedAt == null) throw AcademicForbidden()
        return store.myPendingEnrollmentCount(CountPendingEnrollmentsForStudentDto(actor.id))
    }
}
