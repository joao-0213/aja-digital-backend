package br.com.ajadigital.core.dashboard.configuration

import br.com.ajadigital.core.academic.application.ports.`in`.GetDashboardAcademicInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardAnnouncementsInputPort
import br.com.ajadigital.core.announcements.application.ports.`in`.ListDashboardUpcomingEventsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingStudentAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListDashboardAssessmentsInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectsInputPort
import br.com.ajadigital.core.dashboard.application.ports.`in`.GetDashboardInputPort
import br.com.ajadigital.core.dashboard.application.usecases.GetDashboardUseCase
import br.com.ajadigital.core.forum.application.ports.`in`.ListDashboardTopicsInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetDashboardContentInputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class DashboardConfiguration {
    @Bean
    fun getDashboard(
        subjects: ListSubjectsInputPort,
        dashboardAssessments: ListDashboardAssessmentsInputPort,
        dashboardAnnouncements: ListDashboardAnnouncementsInputPort,
        dashboardUpcoming: ListDashboardUpcomingEventsInputPort,
        dashboardTopics: ListDashboardTopicsInputPort,
        dashboardContent: GetDashboardContentInputPort,
        pendingStudentAssessments: CountPendingStudentAssessmentsInputPort,
        dashboardAcademic: GetDashboardAcademicInputPort,
        pendingFeedback: CountPendingFeedbackInputPort,
        clock: Clock,
    ): GetDashboardInputPort = GetDashboardUseCase(
        subjects,
        dashboardAssessments,
        dashboardAnnouncements,
        dashboardUpcoming,
        dashboardTopics,
        dashboardContent,
        pendingStudentAssessments,
        dashboardAcademic,
        pendingFeedback,
        clock,
    )
}
