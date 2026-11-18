package br.com.ajadigital.core.curriculum.adapters.`in`.controllers
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.ModuleCreateRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.ModuleEditRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.SubjectCreateRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.SubjectEditRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.VisitCommandRequest
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.curriculum.application.contracts.ListSubjectsQuery
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary
import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision
import br.com.ajadigital.core.shared.application.contracts.FieldChange
import br.com.ajadigital.core.shared.application.contracts.Page
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
import br.com.ajadigital.core.identity.application.ports.`in`.ResolveAuthenticatedActorInputPort
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.OrderCommandRequest
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestId
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestIdempotencyKey
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestRevision
import br.com.ajadigital.core.shared.adapters.`in`.web.requests.requestText
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.responses.SubjectSummaryResponse
import tools.jackson.databind.JsonNode


@RestController
class CurriculumController(
    private val auth: ResolveAuthenticatedActorInputPort,
    private val listSubjects: ListSubjectsInputPort,
    private val getSubject: GetSubjectInputPort,
    private val listSubjectModules: ListSubjectModulesInputPort,
    private val createSubject: CreateSubjectInputPort,
    private val updateSubject: UpdateSubjectInputPort,
    private val listModules: ListModulesInputPort,
    private val getModule: GetModuleInputPort,
    private val createModule: CreateModuleInputPort,
    private val updateModule: UpdateModuleInputPort,
    private val reorderModules: ReorderModulesInputPort,
    private val visitSubject: VisitSubjectInputPort,
) {
    @GetMapping("/api/v1/subjects")
    fun subjects(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) q: String?) = subjectPage(listSubjects.subjects(ListSubjectsQuery(actor(cookie), page, pageSize, sort, classId?.let(::requestId), q)))
    @PostMapping("/api/v1/subjects")
    fun createSubject(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: SubjectCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createSubject.createSubject(CreateSubjectCommand(actor(cookie), requestId(body.classId), body.name, body.teacherIds.map(::requestId), body.color, body.icon, body.description, requestIdempotencyKey(key))))
    @GetMapping("/api/v1/subjects/{id}")
    fun subject(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String): Map<String, Any?> {
        val principal = actor(cookie); val subjectId = requestId(id); val subject = getSubject.subject(GetSubjectQuery(principal, subjectId))
        val publicationIds = subject.publicationIds
        return mapOf("subject" to subject.toResponse(), "modules" to listSubjectModules.subjectModules(ListSubjectModulesQuery(principal, subjectId)), "projections" to mapOf("materials" to mapOf("count" to publicationIds.count { it.startsWith("material:") }), "assessments" to mapOf("count" to publicationIds.count { it.startsWith("assessment:") }), "announcements" to mapOf("count" to publicationIds.count { it.startsWith("notice:") })))
    }
    @PatchMapping("/api/v1/subjects/{id}")
    fun updateSubject(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: SubjectEditRequest) = updateSubject.updateSubject(UpdateSubjectCommand(actor(cookie), requestId(id), requestRevision(body.expectedRevision), change(requestText(body.classId)), change(requestText(body.name)), change(requestStrings(body.teacherIds)), change(requestText(body.color)), change(requestText(body.icon)), change(requestText(body.description))))
    @GetMapping("/api/v1/subjects/{id}/visits")
    fun getVisit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = visitSubject.readVisit(actor(cookie), requestId(id))
    @PostMapping("/api/v1/subjects/{id}/visits")
    fun recordVisit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: VisitCommandRequest) = visitSubject.recordVisit(RecordSubjectVisitCommand(actor(cookie), requestId(id), body.publicationIds))

    @GetMapping("/api/v1/modules")
    fun modules(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "order") sort: String, @RequestParam(required = false) subjectId: String?) = listModules.modules(ListModulesQuery(actor(cookie), page, pageSize, sort, subjectId?.let(::requestId)))
    @PostMapping("/api/v1/modules")
    fun createModule(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: ModuleCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createModule.createModule(CreateModuleCommand(actor(cookie), requestId(body.subjectId), body.title, body.description, requestIdempotencyKey(key))))
    @GetMapping("/api/v1/modules/{id}")
    fun module(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getModule.module(GetModuleQuery(actor(cookie), requestId(id)))
    @PatchMapping("/api/v1/modules/{id}")
    fun updateModule(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: ModuleEditRequest) = updateModule.updateModule(UpdateModuleCommand(actor(cookie), requestId(id), requestRevision(body.expectedRevision), change(requestText(body.title)), change(requestText(body.description))))
    @PostMapping("/api/v1/subjects/{id}/reorder-modules")
    fun reorderModules(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestBody body: OrderCommandRequest) = reorderModules.reorder(ReorderModulesCommand(actor(cookie), requestId(id), requestRevision(body.expectedRevision), body.items.map { ModuleRevision(requestId(it.id), it.expectedRevision) }, page, pageSize))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(cookie)
    private fun subjectPage(page: Page<SubjectSummary>) =
        Page(page.items.map { it.toResponse() }, page.page, page.pageSize, page.total)

    private fun <T : Any> change(value: T?): FieldChange<T> =
        if (value == null) FieldChange.Unchanged else FieldChange.Set(value)
    private fun SubjectSummary.toResponse() = SubjectSummaryResponse(subject, className, teachers, canManageContent, publicationIds, hasUpdates)

    private fun requestStrings(value: JsonNode): List<String>? = if (value.isMissingNode) null else { require(value.isArray && (0 until value.size()).all { value.get(it).isTextual }); (0 until value.size()).map { requestId(value.get(it).asString()) } }
}
