package br.com.ajadigital.core.curriculum.application.ports.out

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.RevisionWrite
import br.com.ajadigital.core.curriculum.application.models.SubjectVisit
import br.com.ajadigital.core.curriculum.application.models.store.StoreAuthorizeSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreCreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreListSubjectsQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreModuleQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreModulesQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreReorderModulesCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectModulesQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreSubjectQuery
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreUpdateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.store.StoreVisitSubjectCommand
import br.com.ajadigital.core.shared.application.contracts.Page

interface SubjectStoreOutputPort {
    fun authorize(request: StoreAuthorizeSubjectCommand)
    fun subjects(request: StoreListSubjectsQuery): Page<SubjectSummary>
    fun subject(request: StoreSubjectQuery): SubjectSummary?
    fun subjectModules(request: StoreSubjectModulesQuery): List<Module>
    fun createSubject(request: StoreCreateSubjectCommand): Subject
    fun updateSubject(request: StoreUpdateSubjectCommand): RevisionWrite<Subject>
    fun modules(request: StoreModulesQuery): Page<Module>
    fun module(request: StoreModuleQuery): Module?
    fun createModule(request: StoreCreateModuleCommand): Module
    fun updateModule(request: StoreUpdateModuleCommand): RevisionWrite<Module>
    fun reorderModules(request: StoreReorderModulesCommand): RevisionWrite<Page<Module>>
    fun visit(request: StoreVisitSubjectCommand): SubjectVisit?
}
