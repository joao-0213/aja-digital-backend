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
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentReadOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.AssessmentWriteOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.AttemptStoreOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.assessment.application.ports.out.SubmissionStoreOutputPort
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
    fun createAssessment(store: AssessmentWriteOutputPort, snapshots: QuestionSnapshotProviderOutputPort): CreateAssessmentInputPort =
        CreateAssessmentUseCase(store, snapshots)

    @Bean
    fun updateAssessment(
        store: AssessmentWriteOutputPort, read: AssessmentReadOutputPort, snapshots: QuestionSnapshotProviderOutputPort,
    ): UpdateAssessmentInputPort = UpdateAssessmentUseCase(store, read, snapshots)

    @Bean
    fun publishAssessment(store: AssessmentWriteOutputPort, read: AssessmentReadOutputPort): PublishAssessmentInputPort =
        PublishAssessmentUseCase(store, read)

    @Bean
    fun getAssessment(store: AssessmentReadOutputPort): GetAssessmentInputPort = GetAssessmentUseCase(store)

    @Bean
    fun listAssessments(store: AssessmentReadOutputPort): ListAssessmentsInputPort = ListAssessmentsUseCase(store)

    @Bean
    fun listDashboardAssessments(store: AssessmentReadOutputPort): ListDashboardAssessmentsInputPort = ListDashboardAssessmentsUseCase(store)

    @Bean
    fun countPendingStudentAssessments(store: AssessmentReadOutputPort): CountPendingStudentAssessmentsInputPort = CountPendingStudentAssessmentsUseCase(store)

    @Bean
    fun checkPracticeBlocked(store: AttemptStoreOutputPort): CheckPracticeBlockedInputPort = CheckPracticeBlockedUseCase(store)

    @Bean
    fun startAttempt(store: AttemptStoreOutputPort): StartAttemptInputPort = StartAttemptUseCase(store)

    @Bean
    fun listAttempts(store: AttemptStoreOutputPort): ListAttemptsInputPort = ListAttemptsUseCase(store)

    @Bean
    fun getAttempt(store: AttemptStoreOutputPort): GetAttemptInputPort = GetAttemptUseCase(store)

    @Bean
    fun saveAttemptAnswers(store: AttemptStoreOutputPort): SaveAttemptAnswersInputPort = SaveAttemptAnswersUseCase(store)

    @Bean
    fun submitAttempt(store: AttemptStoreOutputPort): SubmitAttemptInputPort = SubmitAttemptUseCase(store)

    @Bean
    fun getAttemptResult(store: AttemptStoreOutputPort): GetAttemptResultInputPort = GetAttemptResultUseCase(store)

    @Bean
    fun createSubmission(store: SubmissionStoreOutputPort): CreateSubmissionInputPort = CreateSubmissionUseCase(store)

    @Bean
    fun listSubmissions(store: SubmissionStoreOutputPort): ListSubmissionsInputPort = ListSubmissionsUseCase(store)

    @Bean
    fun countPendingFeedback(store: SubmissionStoreOutputPort): CountPendingFeedbackInputPort = CountPendingFeedbackUseCase(store)

    @Bean
    fun getSubmission(store: SubmissionStoreOutputPort): GetSubmissionInputPort = GetSubmissionUseCase(store)

    @Bean
    fun updateSubmission(store: SubmissionStoreOutputPort): UpdateSubmissionInputPort = UpdateSubmissionUseCase(store)

    @Bean
    fun submitSubmission(store: SubmissionStoreOutputPort): SubmitSubmissionInputPort = SubmitSubmissionUseCase(store)

    @Bean
    fun saveSubmissionFeedback(store: SubmissionStoreOutputPort): SaveSubmissionFeedbackInputPort = SaveSubmissionFeedbackUseCase(store)

    @Bean
    fun publishSubmissionFeedback(store: SubmissionStoreOutputPort): PublishSubmissionFeedbackInputPort = PublishSubmissionFeedbackUseCase(store)
}
