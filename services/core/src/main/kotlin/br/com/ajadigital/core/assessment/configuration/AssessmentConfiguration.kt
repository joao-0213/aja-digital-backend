package br.com.ajadigital.core.assessment.configuration

import br.com.ajadigital.core.assessment.domain.ports.`in`.ManageAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ManageAttemptsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ManageSubmissionsUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.assessment.domain.usecases.ManageAssessments
import br.com.ajadigital.core.assessment.domain.usecases.ManageAttempts
import br.com.ajadigital.core.assessment.domain.usecases.ManageSubmissions
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class AssessmentConfiguration {
    @Bean
    fun manageAssessments(
        store: AssessmentStore,
        snapshots: QuestionSnapshotProvider,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
    ): ManageAssessmentsUseCase = ManageAssessments(store, snapshots, idempotency, mapper)

    @Bean
    fun manageAttempts(
        store: AttemptStore,
        mapper: ObjectMapper,
    ): ManageAttemptsUseCase = ManageAttempts(store, mapper)

    @Bean
    fun manageSubmissions(
        store: SubmissionStore,
        mapper: ObjectMapper,
    ): ManageSubmissionsUseCase = ManageSubmissions(store, mapper)
}
