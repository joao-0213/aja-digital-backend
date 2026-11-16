package br.com.ajadigital.core.classroom.configuration

import br.com.ajadigital.core.classroom.domain.ports.`in`.CloseClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CountMyPendingEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.CreateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.DeactivateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetStudentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.GetUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListDashboardClassesUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListMyEnrollmentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListStudentsUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ListUsersUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.PreviewEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.ReopenClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.SetUserRoleUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.TransitionEnrollmentUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateClassUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.UpdateUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.`in`.WithdrawUserUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.classroom.domain.usecases.CloseClass
import br.com.ajadigital.core.classroom.domain.usecases.CountMyPendingEnrollments
import br.com.ajadigital.core.classroom.domain.usecases.CreateClass
import br.com.ajadigital.core.classroom.domain.usecases.CreateEnrollment
import br.com.ajadigital.core.classroom.domain.usecases.CreateUser
import br.com.ajadigital.core.classroom.domain.usecases.DeactivateUser
import br.com.ajadigital.core.classroom.domain.usecases.GetClass
import br.com.ajadigital.core.classroom.domain.usecases.GetEnrollment
import br.com.ajadigital.core.classroom.domain.usecases.GetStudent
import br.com.ajadigital.core.classroom.domain.usecases.GetUser
import br.com.ajadigital.core.classroom.domain.usecases.ListClasses
import br.com.ajadigital.core.classroom.domain.usecases.ListDashboardClasses
import br.com.ajadigital.core.classroom.domain.usecases.ListEnrollments
import br.com.ajadigital.core.classroom.domain.usecases.ListMyEnrollments
import br.com.ajadigital.core.classroom.domain.usecases.ListStudents
import br.com.ajadigital.core.classroom.domain.usecases.ListUsers
import br.com.ajadigital.core.classroom.domain.usecases.PreviewEnrollment
import br.com.ajadigital.core.classroom.domain.usecases.ReopenClass
import br.com.ajadigital.core.classroom.domain.usecases.SetUserRole
import br.com.ajadigital.core.classroom.domain.usecases.TransitionEnrollment
import br.com.ajadigital.core.classroom.domain.usecases.UpdateClass
import br.com.ajadigital.core.classroom.domain.usecases.UpdateUser
import br.com.ajadigital.core.classroom.domain.usecases.WithdrawUser
import br.com.ajadigital.core.identity.adapters.out.security.OutboxEncryption
import br.com.ajadigital.core.identity.domain.ports.out.PasswordHasher
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@Configuration
class ClassroomConfiguration {
    @Bean
    fun listUsers(store: AcademicStore): ListUsersUseCase = ListUsers(store)

    @Bean
    fun getUser(store: AcademicStore): GetUserUseCase = GetUser(store)

    @Bean
    fun createUser(
        store: AcademicStore,
        mapper: ObjectMapper,
        clock: Clock,
        encryption: OutboxEncryption,
        hasher: PasswordHasher,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): CreateUserUseCase = CreateUser(store, mapper, clock, encryption, hasher, idempotency, transactionRunner)

    @Bean
    fun updateUser(store: AcademicStore, transactionRunner: TransactionRunner): UpdateUserUseCase = UpdateUser(store, transactionRunner)

    @Bean
    fun setUserRole(store: AcademicStore, transactionRunner: TransactionRunner): SetUserRoleUseCase = SetUserRole(store, transactionRunner)

    @Bean
    fun deactivateUser(store: AcademicStore, transactionRunner: TransactionRunner): DeactivateUserUseCase = DeactivateUser(store, transactionRunner)

    @Bean
    fun withdrawUser(store: AcademicStore, transactionRunner: TransactionRunner): WithdrawUserUseCase = WithdrawUser(store, transactionRunner)

    @Bean
    fun listClasses(store: AcademicStore): ListClassesUseCase = ListClasses(store)

    @Bean
    fun listDashboardClasses(store: AcademicStore): ListDashboardClassesUseCase = ListDashboardClasses(store)

    @Bean
    fun getClass(store: AcademicStore): GetClassUseCase = GetClass(store)

    @Bean
    fun createClass(
        store: AcademicStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): CreateClassUseCase = CreateClass(store, mapper, idempotency, transactionRunner)

    @Bean
    fun updateClass(store: AcademicStore, transactionRunner: TransactionRunner): UpdateClassUseCase = UpdateClass(store, transactionRunner)

    @Bean
    fun closeClass(
        store: AcademicStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): CloseClassUseCase = CloseClass(store, mapper, idempotency, transactionRunner)

    @Bean
    fun reopenClass(
        store: AcademicStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): ReopenClassUseCase = ReopenClass(store, mapper, idempotency, transactionRunner)

    @Bean
    fun listEnrollments(store: AcademicStore): ListEnrollmentsUseCase = ListEnrollments(store)

    @Bean
    fun getEnrollment(store: AcademicStore): GetEnrollmentUseCase = GetEnrollment(store)

    @Bean
    fun previewEnrollment(store: AcademicStore): PreviewEnrollmentUseCase = PreviewEnrollment(store)

    @Bean
    fun createEnrollment(
        store: AcademicStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): CreateEnrollmentUseCase = CreateEnrollment(store, mapper, idempotency, transactionRunner)

    @Bean
    fun transitionEnrollment(
        store: AcademicStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): TransitionEnrollmentUseCase = TransitionEnrollment(store, mapper, idempotency, transactionRunner)

    @Bean
    fun listMyEnrollments(store: AcademicStore): ListMyEnrollmentsUseCase = ListMyEnrollments(store)

    @Bean
    fun countMyPendingEnrollments(store: AcademicStore): CountMyPendingEnrollmentsUseCase = CountMyPendingEnrollments(store)

    @Bean
    fun listStudents(store: AcademicStore): ListStudentsUseCase = ListStudents(store)

    @Bean
    fun getStudent(store: AcademicStore): GetStudentUseCase = GetStudent(store)
}
