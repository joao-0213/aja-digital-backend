package br.com.ajadigital.core.curriculum.adapters.`in`.controllers
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.ModuleCreateRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.ModuleEditRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.SubjectCreateRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.SubjectEditRequest
import br.com.ajadigital.core.curriculum.adapters.`in`.controllers.models.requests.VisitCommandRequest
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.academic.application.models.AcademicPage
import br.com.ajadigital.core.curriculum.domain.models.*
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
    fun subjects(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "id") sort: String, @RequestParam(required = false) classId: String?, @RequestParam(required = false) q: String?) = pagedBody(listSubjects.subjects(ListSubjectsDto(actor(cookie), page, pageSize, sort, classId?.let(::requestId), q)))
    @PostMapping("/api/v1/subjects")
    fun createSubject(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: SubjectCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createSubject.createSubject(CreateSubjectDto(actor(cookie), requestId(body.classId), body.name, body.teacherIds.map(::requestId), body.color, body.icon, body.description, requestIdempotencyKey(key))))
    @GetMapping("/api/v1/subjects/{id}")
    fun subject(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String): Map<String, Any?> {
        val principal = actor(cookie); val subjectId = requestId(id); val subject = getSubject.subject(GetSubjectDto(principal, subjectId))
        val publicationIds = subject["publicationIds"] as List<*>
        return mapOf("subject" to subject, "modules" to listSubjectModules.subjectModules(ListSubjectModulesDto(principal, subjectId)), "projections" to mapOf("materials" to mapOf("count" to publicationIds.count { (it as String).startsWith("material:") }), "assessments" to mapOf("count" to publicationIds.count { (it as String).startsWith("assessment:") }), "announcements" to mapOf("count" to publicationIds.count { (it as String).startsWith("notice:") })))
    }
    @PatchMapping("/api/v1/subjects/{id}")
    fun updateSubject(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: SubjectEditRequest) = updateSubject.updateSubject(UpdateSubjectDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestText(body.classId), requestText(body.name), requestStrings(body.teacherIds), requestText(body.color), requestText(body.icon), requestText(body.description)))
    @GetMapping("/api/v1/subjects/{id}/visits")
    fun getVisit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = visitSubject.visit(VisitSubjectDto(actor(cookie), requestId(id)))
    @PostMapping("/api/v1/subjects/{id}/visits")
    fun recordVisit(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: VisitCommandRequest) = visitSubject.visit(VisitSubjectDto(actor(cookie), requestId(id), body.publicationIds))

    @GetMapping("/api/v1/modules")
    fun modules(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestParam(defaultValue = "order") sort: String, @RequestParam(required = false) subjectId: String?) = pagedBody(listModules.modules(ListModulesDto(actor(cookie), page, pageSize, sort, subjectId?.let(::requestId))))
    @PostMapping("/api/v1/modules")
    fun createModule(@CookieValue(name = "aja_session", required = false) cookie: String?, @RequestHeader(name = "Idempotency-Key", required = false) key: String?, @RequestBody body: ModuleCreateRequest) = ResponseEntity.status(HttpStatus.CREATED).body(createModule.createModule(CreateModuleDto(actor(cookie), requestId(body.subjectId), body.title, body.description, requestIdempotencyKey(key))))
    @GetMapping("/api/v1/modules/{id}")
    fun module(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String) = getModule.module(GetModuleDto(actor(cookie), requestId(id)))
    @PatchMapping("/api/v1/modules/{id}")
    fun updateModule(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestBody body: ModuleEditRequest) = updateModule.updateModule(UpdateModuleDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), requestText(body.title), requestText(body.description)))
    @PostMapping("/api/v1/subjects/{id}/reorder-modules")
    fun reorderModules(@CookieValue(name = "aja_session", required = false) cookie: String?, @PathVariable id: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(defaultValue = "20") pageSize: Int, @RequestBody body: OrderCommandRequest) = reorderModules.reorder(ReorderModulesDto(actor(cookie), requestId(id), requestRevision(body.expectedRevision), body.items.map { requestId(it.id) to it.expectedRevision }, page, pageSize))

    private fun actor(cookie: String?): AuthenticatedActor = auth.resolve(br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto(cookie))
    private fun pagedBody(result: AcademicPage): Map<String, Any?> = mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    private fun requestStrings(value: JsonNode): List<String>? = if (value.isMissingNode) null else { require(value.isArray && (0 until value.size()).all { value.get(it).isTextual }); (0 until value.size()).map { requestId(value.get(it).asString()) } }
}
