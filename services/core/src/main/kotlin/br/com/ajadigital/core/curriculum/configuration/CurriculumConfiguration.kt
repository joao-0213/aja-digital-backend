package br.com.ajadigital.core.curriculum.configuration

import br.com.ajadigital.core.curriculum.domain.ports.`in`.ManageSubjectsUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.curriculum.domain.usecases.ManageSubjects
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class CurriculumConfiguration {
    @Bean
    fun manageSubjects(
        store: SubjectStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): ManageSubjectsUseCase = ManageSubjects(store, mapper, idempotency, transactionRunner)
}
