package br.com.ajadigital.core.attendance.configuration

import br.com.ajadigital.core.attendance.application.ports.`in`.GetAttendanceSessionInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.ListAttendanceSessionsInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.ListEligibleAttendanceStudentsInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.RecordAttendanceInputPort
import br.com.ajadigital.core.attendance.application.ports.`in`.UpdateAttendanceInputPort
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStoreOutputPort
import br.com.ajadigital.core.attendance.application.usecases.GetAttendanceSessionUseCase
import br.com.ajadigital.core.attendance.application.usecases.ListAttendanceSessionsUseCase
import br.com.ajadigital.core.attendance.application.usecases.ListEligibleAttendanceStudentsUseCase
import br.com.ajadigital.core.attendance.application.usecases.RecordAttendanceUseCase
import br.com.ajadigital.core.attendance.application.usecases.UpdateAttendanceUseCase
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort

@Configuration
class AttendanceConfiguration {
    @Bean
    fun listAttendanceSessions(store: AttendanceStoreOutputPort): ListAttendanceSessionsInputPort =
        ListAttendanceSessionsUseCase(store)

    @Bean
    fun listEligibleAttendanceStudents(store: AttendanceStoreOutputPort): ListEligibleAttendanceStudentsInputPort =
        ListEligibleAttendanceStudentsUseCase(store)

    @Bean
    fun getAttendanceSession(store: AttendanceStoreOutputPort): GetAttendanceSessionInputPort =
        GetAttendanceSessionUseCase(store)

    @Bean
    fun recordAttendance(
        store: AttendanceStoreOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): RecordAttendanceInputPort = RecordAttendanceUseCase(store, idempotency, mapper, transactionRunner)

    @Bean
    fun updateAttendance(
        store: AttendanceStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): UpdateAttendanceInputPort = UpdateAttendanceUseCase(store, transactionRunner)
}
