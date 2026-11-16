package br.com.ajadigital.core.forum.configuration

import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.forum.domain.ports.`in`.CreateTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.GetTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.ListDashboardTopicsUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.ListTopicsUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.MarkTopicReadUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.MarkTopicSolutionUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.ReplyToTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.VoteTopicReplyUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.WithdrawTopicReplyUseCase
import br.com.ajadigital.core.forum.domain.ports.`in`.WithdrawTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.forum.domain.usecases.CreateTopic
import br.com.ajadigital.core.forum.domain.usecases.GetTopic
import br.com.ajadigital.core.forum.domain.usecases.ListDashboardTopics
import br.com.ajadigital.core.forum.domain.usecases.ListTopics
import br.com.ajadigital.core.forum.domain.usecases.MarkTopicRead
import br.com.ajadigital.core.forum.domain.usecases.MarkTopicSolution
import br.com.ajadigital.core.forum.domain.usecases.ReplyToTopic
import br.com.ajadigital.core.forum.domain.usecases.VoteTopicReply
import br.com.ajadigital.core.forum.domain.usecases.WithdrawTopic
import br.com.ajadigital.core.forum.domain.usecases.WithdrawTopicReply
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class ForumConfiguration {
    @Bean
    fun createTopic(
        store: TopicStore,
        questions: QuestionSnapshotProvider,
        mapper: ObjectMapper,
    ): CreateTopicUseCase = CreateTopic(store, questions, mapper)

    @Bean
    fun listTopics(store: TopicStore): ListTopicsUseCase = ListTopics(store)

    @Bean
    fun listDashboardTopics(store: TopicStore): ListDashboardTopicsUseCase = ListDashboardTopics(store)

    @Bean
    fun getTopic(store: TopicStore): GetTopicUseCase = GetTopic(store)

    @Bean
    fun replyToTopic(
        store: TopicStore,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
        transactionRunner: TransactionRunner,
    ): ReplyToTopicUseCase = ReplyToTopic(store, idempotency, mapper, transactionRunner)

    @Bean
    fun markTopicRead(
        store: TopicStore,
        transactionRunner: TransactionRunner,
    ): MarkTopicReadUseCase = MarkTopicRead(store, transactionRunner)

    @Bean
    fun markTopicSolution(
        store: TopicStore,
        transactionRunner: TransactionRunner,
    ): MarkTopicSolutionUseCase = MarkTopicSolution(store, transactionRunner)

    @Bean
    fun withdrawTopic(
        store: TopicStore,
        transactionRunner: TransactionRunner,
    ): WithdrawTopicUseCase = WithdrawTopic(store, transactionRunner)

    @Bean
    fun voteTopicReply(
        store: TopicStore,
        transactionRunner: TransactionRunner,
    ): VoteTopicReplyUseCase = VoteTopicReply(store, transactionRunner)

    @Bean
    fun withdrawTopicReply(
        store: TopicStore,
        transactionRunner: TransactionRunner,
    ): WithdrawTopicReplyUseCase = WithdrawTopicReply(store, transactionRunner)
}
