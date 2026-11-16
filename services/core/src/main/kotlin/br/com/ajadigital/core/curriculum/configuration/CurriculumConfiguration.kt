package br.com.ajadigital.core.curriculum.configuration

import br.com.ajadigital.core.curriculum.domain.ports.`in`.CreateModuleUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.CreateSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.GetModuleUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.GetSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListModulesUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListSubjectModulesUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ListSubjectsUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ReorderModulesUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.UpdateModuleUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.UpdateSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.`in`.VisitSubjectUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.curriculum.domain.usecases.CreateModule
import br.com.ajadigital.core.curriculum.domain.usecases.CreateSubject
import br.com.ajadigital.core.curriculum.domain.usecases.GetModule
import br.com.ajadigital.core.curriculum.domain.usecases.GetSubject
import br.com.ajadigital.core.curriculum.domain.usecases.ListModules
import br.com.ajadigital.core.curriculum.domain.usecases.ListSubjectModules
import br.com.ajadigital.core.curriculum.domain.usecases.ListSubjects
import br.com.ajadigital.core.curriculum.domain.usecases.ReorderModules
import br.com.ajadigital.core.curriculum.domain.usecases.UpdateModule
import br.com.ajadigital.core.curriculum.domain.usecases.UpdateSubject
import br.com.ajadigital.core.curriculum.domain.usecases.VisitSubject
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class CurriculumConfiguration {
    @Bean
    fun listSubjects(store: SubjectStore): ListSubjectsUseCase = ListSubjects(store)

    @Bean
    fun getSubject(store: SubjectStore): GetSubjectUseCase = GetSubject(store)

    @Bean
    fun listSubjectModules(store: SubjectStore): ListSubjectModulesUseCase = ListSubjectModules(store)

    @Bean
    fun createSubject(
        store: SubjectStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): CreateSubjectUseCase = CreateSubject(store, mapper, idempotency, transactionRunner)

    @Bean
    fun updateSubject(
        store: SubjectStore,
        transactionRunner: TransactionRunner,
    ): UpdateSubjectUseCase = UpdateSubject(store, transactionRunner)

    @Bean
    fun listModules(store: SubjectStore): ListModulesUseCase = ListModules(store)

    @Bean
    fun getModule(store: SubjectStore): GetModuleUseCase = GetModule(store)

    @Bean
    fun createModule(
        store: SubjectStore,
        mapper: ObjectMapper,
        idempotency: IdempotencyStore,
        transactionRunner: TransactionRunner,
    ): CreateModuleUseCase = CreateModule(store, mapper, idempotency, transactionRunner)

    @Bean
    fun updateModule(
        store: SubjectStore,
        transactionRunner: TransactionRunner,
    ): UpdateModuleUseCase = UpdateModule(store, transactionRunner)

    @Bean
    fun reorderModules(
        store: SubjectStore,
        transactionRunner: TransactionRunner,
    ): ReorderModulesUseCase = ReorderModules(store, transactionRunner)

    @Bean
    fun visitSubject(
        store: SubjectStore,
        transactionRunner: TransactionRunner,
    ): VisitSubjectUseCase = VisitSubject(store, transactionRunner)
}
