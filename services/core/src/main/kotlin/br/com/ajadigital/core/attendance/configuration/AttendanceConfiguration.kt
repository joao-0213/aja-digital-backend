package br.com.ajadigital.core.attendance.configuration

import br.com.ajadigital.core.attendance.domain.ports.`in`.GetAttendanceSessionUseCase
import br.com.ajadigital.core.attendance.domain.ports.`in`.ListAttendanceSessionsUseCase
import br.com.ajadigital.core.attendance.domain.ports.`in`.ListEligibleAttendanceStudentsUseCase
import br.com.ajadigital.core.attendance.domain.ports.`in`.RecordAttendanceUseCase
import br.com.ajadigital.core.attendance.domain.ports.`in`.UpdateAttendanceUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.attendance.domain.usecases.GetAttendanceSession
import br.com.ajadigital.core.attendance.domain.usecases.ListAttendanceSessions
import br.com.ajadigital.core.attendance.domain.usecases.ListEligibleAttendanceStudents
import br.com.ajadigital.core.attendance.domain.usecases.RecordAttendance
import br.com.ajadigital.core.attendance.domain.usecases.UpdateAttendance
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class AttendanceConfiguration {
    @Bean
    fun listAttendanceSessions(store: AttendanceStore): ListAttendanceSessionsUseCase =
        ListAttendanceSessions(store)

    @Bean
    fun listEligibleAttendanceStudents(store: AttendanceStore): ListEligibleAttendanceStudentsUseCase =
        ListEligibleAttendanceStudents(store)

    @Bean
    fun getAttendanceSession(store: AttendanceStore): GetAttendanceSessionUseCase =
        GetAttendanceSession(store)

    @Bean
    fun recordAttendance(
        store: AttendanceStore,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
        transactionRunner: TransactionRunner,
    ): RecordAttendanceUseCase = RecordAttendance(store, idempotency, mapper, transactionRunner)

    @Bean
    fun updateAttendance(
        store: AttendanceStore,
        transactionRunner: TransactionRunner,
    ): UpdateAttendanceUseCase = UpdateAttendance(store, transactionRunner)
}
