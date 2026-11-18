package br.com.ajadigital.core.curriculum.domain.ports.out
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import br.com.ajadigital.core.curriculum.domain.models.*

interface SubjectStoreOutputPort {
    fun authorize(request: AuthorizeSubjectDto)
    fun subjects(request: ListSubjectsRecordDto): AcademicPage
    fun subject(request: GetStoredSubjectDto): Map<String, Any?>?
    fun subjectModules(request: ListStoredSubjectModulesDto): List<Map<String, Any?>>
    fun createSubject(request: CreateSubjectRecordDto): Map<String, Any?>
    fun updateSubject(request: UpdateSubjectRecordDto): Map<String, Any?>?
    fun modules(request: ListStoredModulesDto): AcademicPage
    fun module(request: GetStoredModuleDto): Map<String, Any?>?
    fun createModule(request: CreateModuleRecordDto): Map<String, Any?>
    fun updateModule(request: UpdateModuleRecordDto): Map<String, Any?>?
    fun reorderModules(request: ReorderModulesRecordDto): AcademicPage?
    fun visit(request: VisitSubjectRecordDto): Map<String, Any?>?
}
