package br.com.ajadigital.core.assessment.configuration

import br.com.ajadigital.core.assessment.application.ports.`in`.CheckPracticeBlockedInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CountPendingStudentAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.CreateSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptResultInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.GetSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListAttemptsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListDashboardAssessmentsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.ListSubmissionsInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.PublishSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveAttemptAnswersInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SaveSubmissionFeedbackInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.StartAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitAttemptInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.SubmitSubmissionInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateAssessmentInputPort
import br.com.ajadigital.core.assessment.application.ports.`in`.UpdateSubmissionInputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStoreOutputPort
import br.com.ajadigital.core.assessment.domain.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.assessment.domain.ports.out.SubmissionStoreOutputPort
import br.com.ajadigital.core.assessment.application.usecases.CheckPracticeBlockedUseCase
import br.com.ajadigital.core.assessment.application.usecases.CountPendingFeedbackUseCase
import br.com.ajadigital.core.assessment.application.usecases.CountPendingStudentAssessmentsUseCase
import br.com.ajadigital.core.assessment.application.usecases.CreateAssessmentUseCase
import br.com.ajadigital.core.assessment.application.usecases.CreateSubmissionUseCase
import br.com.ajadigital.core.assessment.application.usecases.GetAssessmentUseCase
import br.com.ajadigital.core.assessment.application.usecases.GetAttemptUseCase
import br.com.ajadigital.core.assessment.application.usecases.GetAttemptResultUseCase
import br.com.ajadigital.core.assessment.application.usecases.GetSubmissionUseCase
import br.com.ajadigital.core.assessment.application.usecases.ListAssessmentsUseCase
import br.com.ajadigital.core.assessment.application.usecases.ListAttemptsUseCase
import br.com.ajadigital.core.assessment.application.usecases.ListDashboardAssessmentsUseCase
import br.com.ajadigital.core.assessment.application.usecases.ListSubmissionsUseCase
import br.com.ajadigital.core.assessment.application.usecases.PublishAssessmentUseCase
import br.com.ajadigital.core.assessment.application.usecases.PublishSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.application.usecases.SaveAttemptAnswersUseCase
import br.com.ajadigital.core.assessment.application.usecases.SaveSubmissionFeedbackUseCase
import br.com.ajadigital.core.assessment.application.usecases.StartAttemptUseCase
import br.com.ajadigital.core.assessment.application.usecases.SubmitAttemptUseCase
import br.com.ajadigital.core.assessment.application.usecases.SubmitSubmissionUseCase
import br.com.ajadigital.core.assessment.application.usecases.UpdateAssessmentUseCase
import br.com.ajadigital.core.assessment.application.usecases.UpdateSubmissionUseCase
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort

@Configuration
class AssessmentConfiguration {
    @Bean
    fun createAssessment(
        store: AssessmentStoreOutputPort,
        snapshots: QuestionSnapshotProviderOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        mapper: JsonSerializerOutputPort,
    ): CreateAssessmentInputPort = CreateAssessmentUseCase(store, snapshots, idempotency, mapper)

    @Bean
    fun updateAssessment(
        store: AssessmentStoreOutputPort,
        snapshots: QuestionSnapshotProviderOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        mapper: JsonSerializerOutputPort,
    ): UpdateAssessmentInputPort = UpdateAssessmentUseCase(store, snapshots, idempotency, mapper)

    @Bean
    fun publishAssessment(
        store: AssessmentStoreOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        mapper: JsonSerializerOutputPort,
    ): PublishAssessmentInputPort = PublishAssessmentUseCase(store, idempotency, mapper)

    @Bean
    fun getAssessment(store: AssessmentStoreOutputPort): GetAssessmentInputPort = GetAssessmentUseCase(store)

    @Bean
    fun listAssessments(store: AssessmentStoreOutputPort): ListAssessmentsInputPort = ListAssessmentsUseCase(store)

    @Bean
    fun listDashboardAssessments(store: AssessmentStoreOutputPort): ListDashboardAssessmentsInputPort = ListDashboardAssessmentsUseCase(store)

    @Bean
    fun countPendingStudentAssessments(store: AssessmentStoreOutputPort): CountPendingStudentAssessmentsInputPort = CountPendingStudentAssessmentsUseCase(store)

    @Bean
    fun checkPracticeBlocked(store: AttemptStoreOutputPort): CheckPracticeBlockedInputPort = CheckPracticeBlockedUseCase(store)

    @Bean
    fun startAttempt(store: AttemptStoreOutputPort, mapper: JsonSerializerOutputPort): StartAttemptInputPort = StartAttemptUseCase(store, mapper)

    @Bean
    fun listAttempts(store: AttemptStoreOutputPort): ListAttemptsInputPort = ListAttemptsUseCase(store)

    @Bean
    fun getAttempt(store: AttemptStoreOutputPort): GetAttemptInputPort = GetAttemptUseCase(store)

    @Bean
    fun saveAttemptAnswers(store: AttemptStoreOutputPort): SaveAttemptAnswersInputPort = SaveAttemptAnswersUseCase(store)

    @Bean
    fun submitAttempt(store: AttemptStoreOutputPort, mapper: JsonSerializerOutputPort): SubmitAttemptInputPort = SubmitAttemptUseCase(store, mapper)

    @Bean
    fun getAttemptResult(store: AttemptStoreOutputPort): GetAttemptResultInputPort = GetAttemptResultUseCase(store)

    @Bean
    fun createSubmission(store: SubmissionStoreOutputPort, mapper: JsonSerializerOutputPort): CreateSubmissionInputPort = CreateSubmissionUseCase(store, mapper)

    @Bean
    fun listSubmissions(store: SubmissionStoreOutputPort): ListSubmissionsInputPort = ListSubmissionsUseCase(store)

    @Bean
    fun countPendingFeedback(store: SubmissionStoreOutputPort): CountPendingFeedbackInputPort = CountPendingFeedbackUseCase(store)

    @Bean
    fun getSubmission(store: SubmissionStoreOutputPort): GetSubmissionInputPort = GetSubmissionUseCase(store)

    @Bean
    fun updateSubmission(store: SubmissionStoreOutputPort): UpdateSubmissionInputPort = UpdateSubmissionUseCase(store)

    @Bean
    fun submitSubmission(store: SubmissionStoreOutputPort, mapper: JsonSerializerOutputPort): SubmitSubmissionInputPort = SubmitSubmissionUseCase(store, mapper)

    @Bean
    fun saveSubmissionFeedback(store: SubmissionStoreOutputPort): SaveSubmissionFeedbackInputPort = SaveSubmissionFeedbackUseCase(store)

    @Bean
    fun publishSubmissionFeedback(store: SubmissionStoreOutputPort, mapper: JsonSerializerOutputPort): PublishSubmissionFeedbackInputPort = PublishSubmissionFeedbackUseCase(store, mapper)
}
