package br.com.ajadigital.core.forum.configuration

import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.forum.domain.ports.`in`.ManageTopicsUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.forum.domain.usecases.ManageTopics
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class ForumConfiguration {
    @Bean
    fun manageTopics(
        store: TopicStore,
        questions: QuestionSnapshotProvider,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
        transactionRunner: TransactionRunner,
    ): ManageTopicsUseCase = ManageTopics(store, questions, idempotency, mapper, transactionRunner)
}
