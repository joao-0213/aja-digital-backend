package br.com.ajadigital.core.classroom.configuration

import br.com.ajadigital.core.classroom.domain.ports.`in`.ManageAcademicsUseCase
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicStore
import br.com.ajadigital.core.classroom.domain.usecases.ManageAcademics
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
    fun manageAcademics(
        store: AcademicStore,
        mapper: ObjectMapper,
        clock: Clock,
        encryption: OutboxEncryption,
        hasher: PasswordHasher,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): ManageAcademicsUseCase = ManageAcademics(store, mapper, clock, encryption, hasher, idempotency, transactionRunner)
}
