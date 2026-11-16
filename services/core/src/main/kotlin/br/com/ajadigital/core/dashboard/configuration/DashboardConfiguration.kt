package br.com.ajadigital.core.dashboard.configuration

import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardAnnouncementsUseCase
import br.com.ajadigital.core.announcements.domain.ports.`in`.ListDashboardUpcomingEventsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingStudentAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListDashboardAssessmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CountMyPendingEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListDashboardClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListStudentsUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListSubjectsUseCase
import br.com.ajadigital.core.dashboard.domain.ports.`in`.GetDashboardUseCase
import br.com.ajadigital.core.dashboard.domain.usecases.GetDashboard
import br.com.ajadigital.core.forum.domain.ports.`in`.ListDashboardTopicsUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.GetDashboardContentUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class DashboardConfiguration {
    @Bean
    fun getDashboard(
        subjects: ListSubjectsUseCase,
        dashboardAssessments: ListDashboardAssessmentsUseCase,
        dashboardAnnouncements: ListDashboardAnnouncementsUseCase,
        dashboardUpcoming: ListDashboardUpcomingEventsUseCase,
        dashboardTopics: ListDashboardTopicsUseCase,
        dashboardContent: GetDashboardContentUseCase,
        myPendingEnrollments: CountMyPendingEnrollmentsUseCase,
        pendingStudentAssessments: CountPendingStudentAssessmentsUseCase,
        dashboardClasses: ListDashboardClassesUseCase,
        enrollments: ListEnrollmentsUseCase,
        students: ListStudentsUseCase,
        pendingFeedback: CountPendingFeedbackUseCase,
        clock: Clock,
    ): GetDashboardUseCase = GetDashboard(
        subjects,
        dashboardAssessments,
        dashboardAnnouncements,
        dashboardUpcoming,
        dashboardTopics,
        dashboardContent,
        myPendingEnrollments,
        pendingStudentAssessments,
        dashboardClasses,
        enrollments,
        students,
        pendingFeedback,
        clock,
    )
}
