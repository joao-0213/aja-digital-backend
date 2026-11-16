package br.com.ajadigital.core.materials.configuration

import br.com.ajadigital.core.materials.domain.ports.`in`.ManageContentUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore
import br.com.ajadigital.core.materials.domain.usecases.ManageContent
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class MaterialsConfiguration {
    @Bean
    fun manageContent(
        store: ContentStore,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
        transactionRunner: TransactionRunner,
    ): ManageContentUseCase = ManageContent(store, idempotency, mapper, transactionRunner)
}
