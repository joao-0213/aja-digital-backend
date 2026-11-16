package br.com.ajadigital.core.assessment.configuration

import br.com.ajadigital.core.assessment.domain.ports.`in`.CheckPracticeBlockedUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CountPendingStudentAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CreateAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.CreateSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAttemptResultUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.GetAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.GetSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListAttemptsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListDashboardAssessmentsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.ListSubmissionsUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.PublishAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.PublishSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SaveAttemptAnswersUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SaveSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.StartAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SubmitAttemptUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.SubmitSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.UpdateAssessmentUseCase
import br.com.ajadigital.core.assessment.domain.ports.`in`.UpdateSubmissionUseCase
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStore
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStore
import br.com.ajadigital.core.assessment.domain.usecases.CheckPracticeBlocked
import br.com.ajadigital.core.assessment.domain.usecases.CountPendingFeedback
import br.com.ajadigital.core.assessment.domain.usecases.CountPendingStudentAssessments
import br.com.ajadigital.core.assessment.domain.usecases.CreateAssessment
import br.com.ajadigital.core.assessment.domain.usecases.CreateSubmission
import br.com.ajadigital.core.assessment.domain.usecases.GetAssessment
import br.com.ajadigital.core.assessment.domain.usecases.GetAttempt
import br.com.ajadigital.core.assessment.domain.usecases.GetAttemptResult
import br.com.ajadigital.core.assessment.domain.usecases.GetSubmission
import br.com.ajadigital.core.assessment.domain.usecases.ListAssessments
import br.com.ajadigital.core.assessment.domain.usecases.ListAttempts
import br.com.ajadigital.core.assessment.domain.usecases.ListDashboardAssessments
import br.com.ajadigital.core.assessment.domain.usecases.ListSubmissions
import br.com.ajadigital.core.assessment.domain.usecases.PublishAssessment
import br.com.ajadigital.core.assessment.domain.usecases.PublishSubmissionFeedback
import br.com.ajadigital.core.assessment.domain.usecases.SaveAttemptAnswers
import br.com.ajadigital.core.assessment.domain.usecases.SaveSubmissionFeedback
import br.com.ajadigital.core.assessment.domain.usecases.StartAttempt
import br.com.ajadigital.core.assessment.domain.usecases.SubmitAttempt
import br.com.ajadigital.core.assessment.domain.usecases.SubmitSubmission
import br.com.ajadigital.core.assessment.domain.usecases.UpdateAssessment
import br.com.ajadigital.core.assessment.domain.usecases.UpdateSubmission
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class AssessmentConfiguration {
    @Bean
    fun createAssessment(
        store: AssessmentStore,
        snapshots: QuestionSnapshotProvider,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
    ): CreateAssessmentUseCase = CreateAssessment(store, snapshots, idempotency, mapper)

    @Bean
    fun updateAssessment(
        store: AssessmentStore,
        snapshots: QuestionSnapshotProvider,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
    ): UpdateAssessmentUseCase = UpdateAssessment(store, snapshots, idempotency, mapper)

    @Bean
    fun publishAssessment(
        store: AssessmentStore,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
    ): PublishAssessmentUseCase = PublishAssessment(store, idempotency, mapper)

    @Bean
    fun getAssessment(store: AssessmentStore): GetAssessmentUseCase = GetAssessment(store)

    @Bean
    fun listAssessments(store: AssessmentStore): ListAssessmentsUseCase = ListAssessments(store)

    @Bean
    fun listDashboardAssessments(store: AssessmentStore): ListDashboardAssessmentsUseCase = ListDashboardAssessments(store)

    @Bean
    fun countPendingStudentAssessments(store: AssessmentStore): CountPendingStudentAssessmentsUseCase = CountPendingStudentAssessments(store)

    @Bean
    fun checkPracticeBlocked(store: AttemptStore): CheckPracticeBlockedUseCase = CheckPracticeBlocked(store)

    @Bean
    fun startAttempt(store: AttemptStore, mapper: ObjectMapper): StartAttemptUseCase = StartAttempt(store, mapper)

    @Bean
    fun listAttempts(store: AttemptStore): ListAttemptsUseCase = ListAttempts(store)

    @Bean
    fun getAttempt(store: AttemptStore): GetAttemptUseCase = GetAttempt(store)

    @Bean
    fun saveAttemptAnswers(store: AttemptStore): SaveAttemptAnswersUseCase = SaveAttemptAnswers(store)

    @Bean
    fun submitAttempt(store: AttemptStore, mapper: ObjectMapper): SubmitAttemptUseCase = SubmitAttempt(store, mapper)

    @Bean
    fun getAttemptResult(store: AttemptStore): GetAttemptResultUseCase = GetAttemptResult(store)

    @Bean
    fun createSubmission(store: SubmissionStore, mapper: ObjectMapper): CreateSubmissionUseCase = CreateSubmission(store, mapper)

    @Bean
    fun listSubmissions(store: SubmissionStore): ListSubmissionsUseCase = ListSubmissions(store)

    @Bean
    fun countPendingFeedback(store: SubmissionStore): CountPendingFeedbackUseCase = CountPendingFeedback(store)

    @Bean
    fun getSubmission(store: SubmissionStore): GetSubmissionUseCase = GetSubmission(store)

    @Bean
    fun updateSubmission(store: SubmissionStore): UpdateSubmissionUseCase = UpdateSubmission(store)

    @Bean
    fun submitSubmission(store: SubmissionStore, mapper: ObjectMapper): SubmitSubmissionUseCase = SubmitSubmission(store, mapper)

    @Bean
    fun saveSubmissionFeedback(store: SubmissionStore): SaveSubmissionFeedbackUseCase = SaveSubmissionFeedback(store)

    @Bean
    fun publishSubmissionFeedback(store: SubmissionStore, mapper: ObjectMapper): PublishSubmissionFeedbackUseCase = PublishSubmissionFeedback(store, mapper)
}
