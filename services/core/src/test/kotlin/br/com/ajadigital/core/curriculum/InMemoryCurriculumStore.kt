package br.com.ajadigital.core.curriculum

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.models.RevisionWrite
import br.com.ajadigital.core.curriculum.application.models.SubjectVisit
import br.com.ajadigital.core.curriculum.application.models.store.*
import br.com.ajadigital.core.curriculum.application.ports.out.SubjectStoreOutputPort
import br.com.ajadigital.core.curriculum.domain.exceptions.CurriculumForbidden
import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision
import br.com.ajadigital.core.curriculum.domain.policies.CurriculumPolicy
import br.com.ajadigital.core.curriculum.domain.policies.ModuleOrdering
import br.com.ajadigital.core.curriculum.domain.policies.SubjectPublications
import br.com.ajadigital.core.shared.application.contracts.Page
import java.time.Instant

internal class InMemoryCurriculumStore : SubjectStoreOutputPort {
    private val timestamp = Instant.EPOCH.toString()
    private val publications = listOf("material:observed", "notice:later")
    private var seenAt: String? = null
    private var seen = emptyList<String>()
    private var subject = Subject("subject", 1, timestamp, timestamp, "teacher", "class", "Test", listOf("teacher"), "blue", "book", "")
    private var modules = listOf(
        Module("module-a", 1, timestamp, timestamp, "teacher", "subject", "Original title", "Original description", 0),
        Module("module-b", 1, timestamp, timestamp, "teacher", "subject", "Second title", "", 1),
    )

    override fun subjects(request: StoreListSubjectsQuery): Page<SubjectSummary> {
        val item = subject(StoreSubjectQuery(subject.id, request.actorId, request.role))
            ?.takeIf { request.query == null || it.subject.name.contains(request.query, ignoreCase = true) }
        val items = listOfNotNull(item)
        return Page(items.drop((request.page - 1) * request.size).take(request.size), request.page, request.size, items.size)
    }

    override fun subject(request: StoreSubjectQuery): SubjectSummary? =
        if (request.id == subject.id && request.actorId in subject.teacherIds)
            SubjectSummary(subject, "Class", emptyList(), true, publications, SubjectPublications.hasUpdates(publications, seen))
        else null

    override fun subjectModules(request: StoreSubjectModulesQuery): List<Module> =
        if (request.subjectId == subject.id && request.actorId in subject.teacherIds) modules else emptyList()

    override fun updateModule(request: StoreUpdateModuleCommand): RevisionWrite<Module> {
        val current = modules.find { it.id == request.id } ?: return RevisionWrite.Missing
        requireWriter(request.actorId, request.role)
        if (current.revision != request.expectedRevision) return RevisionWrite.Conflict(current.revision)
        val updated = current.copy(
            revision = current.revision + 1,
            title = request.title ?: current.title,
            description = request.description ?: current.description,
        )
        modules = modules.map { if (it.id == updated.id) updated else it }
        return RevisionWrite.Applied(updated)
    }

    override fun reorderModules(request: StoreReorderModulesCommand): RevisionWrite<Page<Module>> {
        if (request.subjectId != subject.id) return RevisionWrite.Missing
        requireWriter(request.actorId, request.role)
        if (request.expectedRevision != subject.revision ||
            !ModuleOrdering.accepts(request.items, modules.map { ModuleRevision(it.id, it.revision) })
        ) return RevisionWrite.Conflict(subject.revision)
        modules = request.items.mapIndexed { index, item ->
            val current = modules.first { it.id == item.id }
            current.copy(order = index, revision = current.revision + 1)
        }
        subject = subject.copy(revision = subject.revision + 1)
        return RevisionWrite.Applied(Page(
            modules.drop((request.page - 1) * request.size).take(request.size), request.page, request.size, modules.size,
        ))
    }

    override fun visit(request: StoreVisitSubjectCommand): SubjectVisit? {
        if (request.subjectId != subject.id || request.actorId !in subject.teacherIds) return null
        val merged = SubjectPublications.seenAfter(seen, request.publicationIds, publications)
        if (request.publicationIds != null) {
            seen = merged
            seenAt = timestamp
        }
        return SubjectVisit(subject.id, seenAt, merged, publications, SubjectPublications.hasUpdates(publications, merged))
    }

    private fun requireWriter(actorId: String, role: String) {
        CurriculumPolicy.requireWriterRole(true, role)
        if (actorId !in subject.teacherIds) throw CurriculumForbidden()
    }

    override fun authorize(request: StoreAuthorizeSubjectCommand): Unit = error("Unexpected authorization call")
    override fun createSubject(request: StoreCreateSubjectCommand): Subject = error("Unexpected subject creation")
    override fun updateSubject(request: StoreUpdateSubjectCommand): RevisionWrite<Subject> = error("Unexpected subject update")
    override fun modules(request: StoreModulesQuery): Page<Module> = error("Unexpected module list")
    override fun module(request: StoreModuleQuery): Module? = error("Unexpected module query")
    override fun createModule(request: StoreCreateModuleCommand): Module = error("Unexpected module creation")
}
