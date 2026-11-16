package br.com.ajadigital.core.attendance.configuration

import br.com.ajadigital.core.attendance.domain.ports.`in`.ManageAttendanceUseCase
import br.com.ajadigital.core.attendance.domain.ports.out.AttendanceStore
import br.com.ajadigital.core.attendance.domain.usecases.ManageAttendance
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class AttendanceConfiguration {
    @Bean
    fun manageAttendance(
        store: AttendanceStore,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
        transactionRunner: TransactionRunner,
    ): ManageAttendanceUseCase = ManageAttendance(store, idempotency, mapper, transactionRunner)
}
