package br.com.ajadigital.core.dashboard.configuration

import br.com.ajadigital.core.announcements.domain.ports.`in`.ManageAnnouncementsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ManageAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ManageSubmissionsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ManageAcademicsUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ManageSubjectsUseCase
import br.com.ajadigital.core.dashboard.domain.ports.`in`.ManageDashboardUseCase
import br.com.ajadigital.core.dashboard.domain.usecases.ManageDashboard
import br.com.ajadigital.core.forum.domain.ports.`in`.ManageTopicsUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.ManageContentUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class DashboardConfiguration {
    @Bean
    fun manageDashboard(
        academics: ManageAcademicsUseCase,
        subjects: ManageSubjectsUseCase,
        content: ManageContentUseCase,
        assessments: ManageAssessmentsUseCase,
        submissions: ManageSubmissionsUseCase,
        announcements: ManageAnnouncementsUseCase,
        topics: ManageTopicsUseCase,
        clock: Clock,
    ): ManageDashboardUseCase = ManageDashboard(academics, subjects, content, assessments, submissions, announcements, topics, clock)
}
