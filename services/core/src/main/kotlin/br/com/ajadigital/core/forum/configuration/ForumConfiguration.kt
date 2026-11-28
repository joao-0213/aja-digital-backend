package br.com.ajadigital.core.forum.configuration

import br.com.ajadigital.core.forum.application.ports.out.ForumQuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.forum.application.ports.`in`.CreateTopicInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.GetTopicInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.ListDashboardTopicsInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.ListTopicsInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicReadInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicSolutionInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.ReplyToTopicInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.VoteTopicReplyInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicReplyInputPort
import br.com.ajadigital.core.forum.application.ports.`in`.WithdrawTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.usecases.CreateTopicUseCase
import br.com.ajadigital.core.forum.application.usecases.GetTopicUseCase
import br.com.ajadigital.core.forum.application.usecases.ListDashboardTopicsUseCase
import br.com.ajadigital.core.forum.application.usecases.ListTopicsUseCase
import br.com.ajadigital.core.forum.application.usecases.MarkTopicReadUseCase
import br.com.ajadigital.core.forum.application.usecases.MarkTopicSolutionUseCase
import br.com.ajadigital.core.forum.application.usecases.ReplyToTopicUseCase
import br.com.ajadigital.core.forum.application.usecases.VoteTopicReplyUseCase
import br.com.ajadigital.core.forum.application.usecases.WithdrawTopicUseCase
import br.com.ajadigital.core.forum.application.usecases.WithdrawTopicReplyUseCase
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import br.com.ajadigital.core.forum.application.ports.out.ForumRequestFingerprintOutputPort

@Configuration
class ForumConfiguration {
    @Bean
    fun createTopic(
        store: TopicStoreOutputPort,
        questions: ForumQuestionSnapshotProviderOutputPort,
        fingerprints: ForumRequestFingerprintOutputPort,
    ): CreateTopicInputPort = CreateTopicUseCase(store, questions, fingerprints)

    @Bean
    fun listTopics(store: TopicStoreOutputPort): ListTopicsInputPort = ListTopicsUseCase(store)

    @Bean
    fun listDashboardTopics(store: TopicStoreOutputPort): ListDashboardTopicsInputPort = ListDashboardTopicsUseCase(store)

    @Bean
    fun getTopic(store: TopicStoreOutputPort): GetTopicInputPort = GetTopicUseCase(store)

    @Bean
    fun replyToTopic(
        store: TopicStoreOutputPort,
        fingerprints: ForumRequestFingerprintOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): ReplyToTopicInputPort = ReplyToTopicUseCase(store, fingerprints, transactionRunner)

    @Bean
    fun markTopicRead(
        store: TopicStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): MarkTopicReadInputPort = MarkTopicReadUseCase(store, transactionRunner)

    @Bean
    fun markTopicSolution(
        store: TopicStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): MarkTopicSolutionInputPort = MarkTopicSolutionUseCase(store, transactionRunner)

    @Bean
    fun withdrawTopic(
        store: TopicStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): WithdrawTopicInputPort = WithdrawTopicUseCase(store, transactionRunner)

    @Bean
    fun voteTopicReply(
        store: TopicStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): VoteTopicReplyInputPort = VoteTopicReplyUseCase(store, transactionRunner)

    @Bean
    fun withdrawTopicReply(
        store: TopicStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): WithdrawTopicReplyInputPort = WithdrawTopicReplyUseCase(store, transactionRunner)
}
