package br.com.ajadigital.core.classroom.domain.usecases

import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.`in`.CountMyPendingEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore

class CountMyPendingEnrollments(private val store: AcademicStore) : CountMyPendingEnrollmentsUseCase {
    override fun myPendingEnrollmentCount(actor: AcademicActor): Int {
        if (actor.role != "student" || !actor.active || actor.emailConfirmedAt == null) throw AcademicForbidden()
        return store.myPendingEnrollmentCount(actor.id)
    }
}
