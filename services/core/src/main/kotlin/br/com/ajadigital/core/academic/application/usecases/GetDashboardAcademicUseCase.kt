package br.com.ajadigital.core.academic.application.usecases

import br.com.ajadigital.core.academic.application.contracts.DashboardAcademicProjection
import br.com.ajadigital.core.academic.application.contracts.DashboardAcademicQuery
import br.com.ajadigital.core.academic.application.contracts.DashboardClassProjection
import br.com.ajadigital.core.academic.application.models.CountMyPendingEnrollmentsDto
import br.com.ajadigital.core.academic.application.models.ListDashboardClassesDto
import br.com.ajadigital.core.academic.application.models.ListEnrollmentsDto
import br.com.ajadigital.core.academic.application.models.ListStudentsDto
import br.com.ajadigital.core.academic.application.ports.`in`.CountMyPendingEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetDashboardAcademicInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListDashboardClassesInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort

class GetDashboardAcademicUseCase(
    private val classes: ListDashboardClassesInputPort,
    private val enrollments: ListEnrollmentsInputPort,
    private val students: ListStudentsInputPort,
    private val myPendingEnrollments: CountMyPendingEnrollmentsInputPort,
) : GetDashboardAcademicInputPort {
    override fun dashboard(query: DashboardAcademicQuery): DashboardAcademicProjection {
        val actor = query.actor
        if (actor.role == "student") {
            return DashboardAcademicProjection(
                emptyList(),
                myPendingEnrollments.myPendingEnrollmentCount(CountMyPendingEnrollmentsDto(actor)),
                0,
            )
        }
        val classPage = classes.dashboardClasses(ListDashboardClassesDto(actor, 100))
        val enrollmentPage = enrollments.enrollments(ListEnrollmentsDto(actor, 1, 1, "id", null, null, listOf("pending"), null))
        val studentPage = students.students(ListStudentsDto(actor, 1, 1, "id", null, null, false))
        return DashboardAcademicProjection(classPage, enrollmentPage.total, studentPage.total)
    }
}
