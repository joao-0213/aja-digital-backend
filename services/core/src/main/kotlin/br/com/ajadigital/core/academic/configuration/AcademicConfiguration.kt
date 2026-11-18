package br.com.ajadigital.core.academic.configuration

import br.com.ajadigital.core.academic.application.ports.`in`.CloseClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CountMyPendingEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.CreateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.DeactivateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetStudentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.GetUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListClassesInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListDashboardClassesInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListMyEnrollmentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListStudentsInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ListUsersInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.PreviewEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.ReopenClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.SetUserRoleInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.TransitionEnrollmentInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateClassInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.UpdateUserInputPort
import br.com.ajadigital.core.academic.application.ports.`in`.WithdrawUserInputPort
import br.com.ajadigital.core.academic.application.ports.out.AcademicStoreOutputPort
import br.com.ajadigital.core.academic.application.usecases.CloseClassUseCase
import br.com.ajadigital.core.academic.application.usecases.CountMyPendingEnrollmentsUseCase
import br.com.ajadigital.core.academic.application.usecases.CreateClassUseCase
import br.com.ajadigital.core.academic.application.usecases.CreateEnrollmentUseCase
import br.com.ajadigital.core.academic.application.usecases.CreateUserUseCase
import br.com.ajadigital.core.academic.application.usecases.DeactivateUserUseCase
import br.com.ajadigital.core.academic.application.usecases.GetClassUseCase
import br.com.ajadigital.core.academic.application.usecases.GetEnrollmentUseCase
import br.com.ajadigital.core.academic.application.usecases.GetStudentUseCase
import br.com.ajadigital.core.academic.application.usecases.GetUserUseCase
import br.com.ajadigital.core.academic.application.usecases.ListClassesUseCase
import br.com.ajadigital.core.academic.application.usecases.ListDashboardClassesUseCase
import br.com.ajadigital.core.academic.application.usecases.ListEnrollmentsUseCase
import br.com.ajadigital.core.academic.application.usecases.ListMyEnrollmentsUseCase
import br.com.ajadigital.core.academic.application.usecases.ListStudentsUseCase
import br.com.ajadigital.core.academic.application.usecases.ListUsersUseCase
import br.com.ajadigital.core.academic.application.usecases.PreviewEnrollmentUseCase
import br.com.ajadigital.core.academic.application.usecases.ReopenClassUseCase
import br.com.ajadigital.core.academic.application.usecases.SetUserRoleUseCase
import br.com.ajadigital.core.academic.application.usecases.TransitionEnrollmentUseCase
import br.com.ajadigital.core.academic.application.usecases.UpdateClassUseCase
import br.com.ajadigital.core.academic.application.usecases.UpdateUserUseCase
import br.com.ajadigital.core.academic.application.usecases.WithdrawUserUseCase
import br.com.ajadigital.core.outbox.application.ports.out.OutboxPayloadProtectorOutputPort
import br.com.ajadigital.core.identity.application.ports.out.PasswordHasherOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import java.time.Clock

@Configuration
class AcademicConfiguration {
    @Bean
    fun listUsers(store: AcademicStoreOutputPort): ListUsersInputPort = ListUsersUseCase(store)

    @Bean
    fun getUser(store: AcademicStoreOutputPort): GetUserInputPort = GetUserUseCase(store)

    @Bean
    fun createUser(
        store: AcademicStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        clock: Clock,
        encryption: OutboxPayloadProtectorOutputPort,
        hasher: PasswordHasherOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): CreateUserInputPort = CreateUserUseCase(store, mapper, clock, encryption, hasher, idempotency, transactionRunner)

    @Bean
    fun updateUser(store: AcademicStoreOutputPort, transactionRunner: TransactionRunnerOutputPort): UpdateUserInputPort = UpdateUserUseCase(store, transactionRunner)

    @Bean
    fun setUserRole(store: AcademicStoreOutputPort, transactionRunner: TransactionRunnerOutputPort): SetUserRoleInputPort = SetUserRoleUseCase(store, transactionRunner)

    @Bean
    fun deactivateUser(store: AcademicStoreOutputPort, transactionRunner: TransactionRunnerOutputPort): DeactivateUserInputPort = DeactivateUserUseCase(store, transactionRunner)

    @Bean
    fun withdrawUser(store: AcademicStoreOutputPort, transactionRunner: TransactionRunnerOutputPort): WithdrawUserInputPort = WithdrawUserUseCase(store, transactionRunner)

    @Bean
    fun listClasses(store: AcademicStoreOutputPort): ListClassesInputPort = ListClassesUseCase(store)

    @Bean
    fun listDashboardClasses(store: AcademicStoreOutputPort): ListDashboardClassesInputPort = ListDashboardClassesUseCase(store)

    @Bean
    fun getClass(store: AcademicStoreOutputPort): GetClassInputPort = GetClassUseCase(store)

    @Bean
    fun createClass(
        store: AcademicStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): CreateClassInputPort = CreateClassUseCase(store, mapper, idempotency, transactionRunner)

    @Bean
    fun updateClass(store: AcademicStoreOutputPort, transactionRunner: TransactionRunnerOutputPort): UpdateClassInputPort = UpdateClassUseCase(store, transactionRunner)

    @Bean
    fun closeClass(
        store: AcademicStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): CloseClassInputPort = CloseClassUseCase(store, mapper, idempotency, transactionRunner)

    @Bean
    fun reopenClass(
        store: AcademicStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): ReopenClassInputPort = ReopenClassUseCase(store, mapper, idempotency, transactionRunner)

    @Bean
    fun listEnrollments(store: AcademicStoreOutputPort): ListEnrollmentsInputPort = ListEnrollmentsUseCase(store)

    @Bean
    fun getEnrollment(store: AcademicStoreOutputPort): GetEnrollmentInputPort = GetEnrollmentUseCase(store)

    @Bean
    fun previewEnrollment(store: AcademicStoreOutputPort): PreviewEnrollmentInputPort = PreviewEnrollmentUseCase(store)

    @Bean
    fun createEnrollment(
        store: AcademicStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): CreateEnrollmentInputPort = CreateEnrollmentUseCase(store, mapper, idempotency, transactionRunner)

    @Bean
    fun transitionEnrollment(
        store: AcademicStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): TransitionEnrollmentInputPort = TransitionEnrollmentUseCase(store, mapper, idempotency, transactionRunner)

    @Bean
    fun listMyEnrollments(store: AcademicStoreOutputPort): ListMyEnrollmentsInputPort = ListMyEnrollmentsUseCase(store)

    @Bean
    fun countMyPendingEnrollments(store: AcademicStoreOutputPort): CountMyPendingEnrollmentsInputPort = CountMyPendingEnrollmentsUseCase(store)

    @Bean
    fun listStudents(store: AcademicStoreOutputPort): ListStudentsInputPort = ListStudentsUseCase(store)

    @Bean
    fun getStudent(store: AcademicStoreOutputPort): GetStudentInputPort = GetStudentUseCase(store)
}
