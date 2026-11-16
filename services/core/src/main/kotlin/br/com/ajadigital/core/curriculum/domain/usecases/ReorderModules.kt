package br.com.ajadigital.core.curriculum.domain.usecases

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.curriculum.domain.ports.`in`.ReorderModulesUseCase
import br.com.ajadigital.core.curriculum.domain.ports.out.SubjectStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner

class ReorderModules(
    private val store: SubjectStore,
    private val transactionRunner: TransactionRunner,
) : ReorderModulesUseCase {
    override fun reorder(actor: AcademicActor, subjectId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, pageSize: Int): Map<String, Any?> = transactionRunner.run {
        CurriculumSupport.requireWriterRole(actor)
        require(revision > 0 && items.isNotEmpty() && items.map { it.first }.distinct().size == items.size && items.all { it.second > 0 })
        val result = store.reorderModules(subjectId, actor.id, actor.role, actor.sessionTokenHash, revision, items, CurriculumSupport.checkedPage(page), CurriculumSupport.checkedSize(pageSize)) ?: CurriculumSupport.conflict(store.subject(subjectId, actor.id, actor.role, actor.sessionTokenHash))
        mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }
}
