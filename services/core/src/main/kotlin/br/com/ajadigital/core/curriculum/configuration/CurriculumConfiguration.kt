package br.com.ajadigital.core.curriculum.configuration

import br.com.ajadigital.core.curriculum.application.ports.`in`.CreateModuleInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.CreateSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.GetModuleInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.GetSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListModulesInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectModulesInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.ListSubjectsInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.ReorderModulesInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.UpdateModuleInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.UpdateSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.`in`.VisitSubjectInputPort
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.application.usecases.CreateModuleUseCase
import br.com.ajadigital.core.curriculum.application.usecases.CreateSubjectUseCase
import br.com.ajadigital.core.curriculum.application.usecases.GetModuleUseCase
import br.com.ajadigital.core.curriculum.application.usecases.GetSubjectUseCase
import br.com.ajadigital.core.curriculum.application.usecases.ListModulesUseCase
import br.com.ajadigital.core.curriculum.application.usecases.ListSubjectModulesUseCase
import br.com.ajadigital.core.curriculum.application.usecases.ListSubjectsUseCase
import br.com.ajadigital.core.curriculum.application.usecases.ReorderModulesUseCase
import br.com.ajadigital.core.curriculum.application.usecases.UpdateModuleUseCase
import br.com.ajadigital.core.curriculum.application.usecases.UpdateSubjectUseCase
import br.com.ajadigital.core.curriculum.application.usecases.VisitSubjectUseCase
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import br.com.ajadigital.core.curriculum.application.ports.out.CurriculumReceiptCodecOutputPort

@Configuration
class CurriculumConfiguration {
    @Bean
    fun listSubjects(store: SubjectStoreOutputPort): ListSubjectsInputPort = ListSubjectsUseCase(store)

    @Bean
    fun getSubject(store: SubjectStoreOutputPort): GetSubjectInputPort = GetSubjectUseCase(store)

    @Bean
    fun listSubjectModules(store: SubjectStoreOutputPort): ListSubjectModulesInputPort = ListSubjectModulesUseCase(store)

    @Bean
    fun createSubject(
        store: SubjectStoreOutputPort,
        codec: CurriculumReceiptCodecOutputPort,
        idempotency: EncodedIdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): CreateSubjectInputPort = CreateSubjectUseCase(store, codec, idempotency, transactionRunner)

    @Bean
    fun updateSubject(
        store: SubjectStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): UpdateSubjectInputPort = UpdateSubjectUseCase(store, transactionRunner)

    @Bean
    fun listModules(store: SubjectStoreOutputPort): ListModulesInputPort = ListModulesUseCase(store)

    @Bean
    fun getModule(store: SubjectStoreOutputPort): GetModuleInputPort = GetModuleUseCase(store)

    @Bean
    fun createModule(
        store: SubjectStoreOutputPort,
        codec: CurriculumReceiptCodecOutputPort,
        idempotency: EncodedIdempotencyStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): CreateModuleInputPort = CreateModuleUseCase(store, codec, idempotency, transactionRunner)

    @Bean
    fun updateModule(
        store: SubjectStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): UpdateModuleInputPort = UpdateModuleUseCase(store, transactionRunner)

    @Bean
    fun reorderModules(
        store: SubjectStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): ReorderModulesInputPort = ReorderModulesUseCase(store, transactionRunner)

    @Bean
    fun visitSubject(
        store: SubjectStoreOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): VisitSubjectInputPort = VisitSubjectUseCase(store, transactionRunner)
}
