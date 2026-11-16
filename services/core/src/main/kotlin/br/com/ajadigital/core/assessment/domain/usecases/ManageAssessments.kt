package br.com.ajadigital.core.assessment.domain.usecases
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable

import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.policies.AssessmentEditPolicy
import br.com.ajadigital.core.assessment.domain.ports.out.AssessmentStore
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.domain.ports.`in`.ManageAssessmentsUseCase
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID


class ManageAssessments(private val store: AssessmentStore, private val snapshots: QuestionSnapshotProvider, private val idempotency: IdempotencyStore, private val mapper: ObjectMapper) : ManageAssessmentsUseCase {
    override fun create(actor: AcademicActor, key: String, body: JsonNode): Map<String, Any?> {
        writer(actor)
        validKey(key)
        val draft = draft(body)
        val questions = entries(body.get("questions"), draft.kind, null)
        store.authorizeWrite(actor.id, actor.role, actor.sessionTokenHash, draft.moduleId)
        val hash = requestHash(body)
        findReplay(actor, "createAssessment", "/assessments", key, hash)?.let { return it }
        val prepared = capture(questions, emptySet())
        return store.create(actor.id, actor.role, actor.sessionTokenHash, key, hash, draft, prepared)
    }

    override fun update(actor: AcademicActor, id: String, key: String, body: JsonNode): Map<String, Any?> {
        writer(actor)
        id(id)
        validKey(key)
        val hash = requestHash(body)
        val expectedRevision = body.get("expectedRevision")?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue()?.takeIf { it > 0 } ?: throw IllegalArgumentException()
        val current = store.assessment(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
        findReplay(actor, "updateAssessment", "/assessments/$id", key, hash)?.let { return it }
        val questionsSupplied = body.has("questions")
        val requestedDraft = partialDraft(body, current)
        store.authorizeWrite(actor.id, actor.role, actor.sessionTokenHash, current["moduleId"] as String)
        if (requestedDraft.moduleId != current["moduleId"]) store.authorizeWrite(actor.id, actor.role, actor.sessionTokenHash, requestedDraft.moduleId)
        val questions = if (questionsSupplied) entries(body.get("questions"), requestedDraft.kind, current) else null
        val effectiveQuestionCount = questions?.size ?: (current["questionIds"] as? List<*>)?.size ?: 0
        if (!AssessmentEditPolicy.isValidKindQuestionCount(requestedDraft.kind, effectiveQuestionCount)) {
            throw AcademicConflict(current["revision"] as? Int)
        }
        val reset = body.get("confirmResetDrafts")?.booleanValue()?.also { require(body.get("confirmResetDrafts").isBoolean) } ?: false
        if (questions != null && questionsChanged(current, questions) && !reset) throw AcademicConflict(current["revision"] as? Int)
        val existingIds = (current["questionIds"] as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
        val prepared = questions?.let { capture(it, existingIds) }
        return store.update(actor.id, actor.role, actor.sessionTokenHash, id, key, hash, expectedRevision, requestedDraft, prepared, reset)
            ?: throw AcademicConflict(current["revision"] as? Int)
    }

    override fun publish(actor: AcademicActor, id: String, key: String, expectedRevision: Int, body: JsonNode): Map<String, Any?> {
        writer(actor)
        id(id); require(expectedRevision > 0)
        validKey(key)
        val hash = requestHash(body)
        require(body.isObject && fieldNames(body) == setOf("expectedRevision"))
        store.assessment(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
        findReplay(actor, "publishAssessment", "/assessments/$id/publish", key, hash)?.let { return it }
        val current = store.publish(actor.id, actor.role, actor.sessionTokenHash, id, key, hash, expectedRevision)
            ?: throw AcademicConflict(store.assessment(actor.id, actor.role, actor.sessionTokenHash, id)?.get("revision") as? Int)
        return current
    }

    override fun assessment(actor: AcademicActor, id: String) = run {
        reader(actor)
        id(id)
        store.assessment(actor.id, actor.role, actor.sessionTokenHash, id) ?: throw AcademicNotFound()
    }

    override fun assessments(actor: AcademicActor, page: Int, pageSize: Int, sort: String, subjectId: String?, moduleId: String?, kind: String?, status: String?): Map<String, Any?> = run {
        reader(actor)
        require(page > 0 && pageSize in 1..100 && sort in setOf("id", "-id"))
        require(subjectId == null || validId(subjectId)); require(moduleId == null || validId(moduleId))
        require(kind == null || kind in setOf("quiz", "work")); require(status == null || status in setOf("draft", "published"))
        val result = store.assessments(actor.id, actor.role, actor.sessionTokenHash, page, pageSize, sort, subjectId, moduleId, kind, status)
        mapOf("items" to result.items, "page" to result.page, "pageSize" to result.pageSize, "total" to result.total)
    }

    override fun dashboardAssessments(actor: AcademicActor, pageSize: Int): Map<String, Any?> = reader(actor).let {
        val page = store.dashboardAssessments(actor.id, actor.role, actor.sessionTokenHash, pageSize)
        mapOf("items" to page.items, "page" to page.page, "pageSize" to page.pageSize, "total" to page.total)
    }

    override fun pendingStudentCount(actor: AcademicActor): Int {
        reader(actor)
        require(actor.role == "student") { throw AcademicForbidden() }
        return store.pendingStudentCount(actor.id, actor.sessionTokenHash)
    }

    private fun draft(body: JsonNode): AssessmentDraft {
        require(body.isObject && fieldNames(body) == createFields)
        val moduleId = text(body, "moduleId").also(::id)
        val title = text(body, "title").trim().also { require(it.isNotEmpty() && it.length <= 160) }
        val description = text(body, "description").also { require(it.length <= 10000) }
        val kind = text(body, "kind").also { require(it in setOf("quiz", "work")) }
        val maxGrade = nullableNumber(body.get("maxGrade"))?.also { require(it > 0 && it.isFinite()) }
        val deadline = nullableInstant(body.get("deadline"))
        val required = bool(body, "required")
        val allowLate = bool(body, "allowLate")
        return AssessmentDraft(moduleId, title, description, kind, maxGrade, deadline, required, allowLate)
    }

    private fun partialDraft(body: JsonNode, current: Map<String, Any?>): AssessmentDraft {
        require(body.isObject && fieldNames(body).all { it in editFields })
        val moduleId = if (body.has("moduleId")) text(body, "moduleId").also(::id) else current["moduleId"] as String
        val title = if (body.has("title")) text(body, "title").trim().also { require(it.isNotEmpty() && it.length <= 160) } else current["title"] as String
        val description = if (body.has("description")) text(body, "description").also { require(it.length <= 10000) } else current["description"] as String
        val kind = if (body.has("kind")) text(body, "kind").also { require(it in setOf("quiz", "work")) } else current["kind"] as String
        val maxGrade = if (body.has("maxGrade")) nullableNumber(body.get("maxGrade"))?.also { require(it > 0 && it.isFinite()) } else current["maxGrade"] as Double?
        val deadline = if (body.has("deadline")) nullableInstant(body.get("deadline")) else current["deadline"]?.let { Instant.parse(it as String) }
        val required = body.get("required")?.booleanValue()?.also { require(body.get("required").isBoolean) } ?: current["required"] as Boolean
        val allowLate = body.get("allowLate")?.booleanValue()?.also { require(body.get("allowLate").isBoolean) } ?: current["allowLate"] as Boolean
        return AssessmentDraft(moduleId, title, description, kind, maxGrade, deadline, required, allowLate)
    }

    private fun entries(value: JsonNode?, kind: String, current: Map<String, Any?>?): List<AssessmentQuestion> {
        val items = value?.takeIf { it.isArray } ?: throw IllegalArgumentException()
        require(items.size() <= 100 && (kind != "work" || items.isEmpty) && (kind != "quiz" || !items.isEmpty))
        val old = (current?.get("questions") as? List<*>)?.filterIsInstance<Map<*, *>>()?.associateBy { it["id"] as String }.orEmpty()
        val ids = mutableSetOf<String>()
        val clientKeys = mutableSetOf<String>()
        return List(items.size()) { index ->
            val entry = items.get(index)
            require(entry.isObject)
            when (text(entry, "source")) {
                "bank" -> {
                    require(fieldNames(entry) == setOf("source", "questionId"))
                    val questionId = text(entry, "questionId").also(::id)
                    require(ids.add(questionId))
                    val prior = old[questionId]
                    require(prior == null || prior["origin"] == "bank")
                    AssessmentQuestion(questionId, "bank", null, prior?.get("sourceRevision") as? Int, prior?.get("contentHash") as? String)
                }
                "authored" -> {
                    require(fieldNames(entry) == setOf("source", "clientKey", "questionId", "question"))
                    val clientKey = text(entry, "clientKey").also(::id)
                    require(clientKeys.add(clientKey))
                    val requestedId = entry.get("questionId")
                    if (requestedId != null && !requestedId.isNull) require(current != null && requestedId.isTextual && old[requestedId.asString()]?.get("origin") == "author")
                    val questionId = if (requestedId == null || requestedId.isNull) "author-${UUID.randomUUID()}" else requestedId.asString().also(::id)
                    require(ids.add(questionId))
                    val authored = entry.get("question")
                    AssessmentQuestion(questionId, "author", authoredSnapshot(questionId, authored), 1, null)
                }
                else -> throw IllegalArgumentException()
            }
        }
    }

    private fun capture(entries: List<AssessmentQuestion>, existingIds: Set<String>): List<AssessmentQuestion> {
        val needed = entries.filter { it.source == "bank" && it.id !in existingIds }.map { it.id }
        if (needed.isEmpty()) return entries
        val snapshotsById = snapshots.snapshots(needed).associateBy(BankQuestionSnapshot::questionId)
        if (snapshotsById.keys != needed.toSet()) throw QuestionsUnavailable()
        return entries.map { entry ->
            if (entry.source != "bank" || entry.id in existingIds) entry else {
                val snapshot = snapshotsById.getValue(entry.id)
                if (snapshot.revision < 1 || !snapshot.contentHash.matches(Regex("^[a-f0-9]{64}$")) || snapshot.options.size !in 2..20 || snapshot.correctOption !in snapshot.options.indices || snapshot.difficulty !in setOf("easy", "medium", "hard") || snapshot.image?.fileId?.let(::validId) == false) throw QuestionsUnavailable()
                entry.copy(snapshot = bankSnapshot(snapshot), sourceRevision = snapshot.revision, contentHash = snapshot.contentHash)
            }
        }
    }

    private fun questionsChanged(current: Map<String, Any?>, desired: List<AssessmentQuestion>): Boolean {
        val old = (current["questions"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        return old.size != desired.size || desired.indices.any { index ->
            val prior = old[index]
            prior["id"] != desired[index].id || prior["origin"] != (if (desired[index].source == "bank") "bank" else "author") || desired[index].snapshot?.let { it != prior } == true
        }
    }

    private fun authoredSnapshot(id: String, question: JsonNode): Map<String, Any?> {
        require(question.isObject && fieldNames(question) == authoredFields)
        val subject = text(question, "subject"); val topic = text(question, "topic")
        val difficulty = text(question, "difficulty").also { require(it in setOf("easy", "medium", "hard")) }
        val institution = text(question, "institution")
        val yearNode = question.get("year")
        val year = yearNode?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue()?.takeIf { it >= 0 } ?: throw IllegalArgumentException()
        val statement = text(question, "statement")
        val optionsNode = question.get("options")
        require(optionsNode?.isArray == true && optionsNode.size() in 2..20 && (0 until optionsNode.size()).all { optionsNode.get(it).isTextual })
        val options = (0 until optionsNode.size()).map { optionsNode.get(it).asString() }
        val correctNode = question.get("correct")
        val correct = correctNode?.takeIf { it.isIntegralNumber && it.canConvertToInt() }?.intValue()?.takeIf { it in options.indices } ?: throw IllegalArgumentException()
        val explanation = text(question, "explanation")
        val image = question.get("image")?.takeUnless(JsonNode::isNull)?.let { imageNode ->
            require(imageNode.isObject && fieldNames(imageNode) == setOf("src", "alt"))
            val src = text(imageNode, "src"); val alt = text(imageNode, "alt")
            require(src.matches(Regex("^/questions/[A-Za-z0-9_-]+\\.(?:svg|png|jpe?g|webp)$")))
            mapOf("src" to src, "alt" to alt)
        }
        return mapOf("id" to id, "origin" to "author", "subject" to subject, "topic" to topic, "difficulty" to difficulty, "institution" to institution, "year" to year, "statement" to statement, "options" to options, "image" to image, "correct" to correct, "explanation" to explanation)
    }

    private fun bankSnapshot(snapshot: BankQuestionSnapshot) = mapOf(
        "id" to snapshot.questionId, "origin" to "bank", "subject" to snapshot.subject, "topic" to snapshot.topic,
        "difficulty" to snapshot.difficulty, "institution" to snapshot.institution, "year" to snapshot.year,
        "statement" to snapshot.statement, "options" to snapshot.options,
        "image" to snapshot.image?.let { mapOf("src" to "/api/v1/files/${it.fileId}/content", "alt" to it.alt) },
        "correct" to snapshot.correctOption, "explanation" to snapshot.explanation,
    )

    private fun nullableNumber(node: JsonNode?): Double? = when { node == null || node.isNull -> null; node.isNumber -> node.doubleValue(); else -> throw IllegalArgumentException() }
    private fun nullableInstant(node: JsonNode?): Instant? = when {
        node == null || node.isNull -> null
        node.isTextual -> try { Instant.parse(node.asString()) } catch (_: java.time.format.DateTimeParseException) { throw IllegalArgumentException() }
        else -> throw IllegalArgumentException()
    }
    private fun bool(node: JsonNode, key: String) = node.get(key)?.booleanValue()?.also { require(node.get(key).isBoolean) } ?: throw IllegalArgumentException()
    private fun text(node: JsonNode, key: String) = node.get(key)?.asString()?.also { require(node.get(key).isTextual) } ?: throw IllegalArgumentException()
    private fun id(value: String) = value.also { require(it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))) }
    private fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
    private fun validKey(value: String) { require(value.length in 8..128) }
    private fun requestHash(body: JsonNode) = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body)).joinToString("") { "%02x".format(it) }
    private fun findReplay(actor: AcademicActor, operation: String, target: String, key: String, hash: String): Map<String, Any?>? = try { idempotency.find(actor.id, operation, target, key, hash) } catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
    private fun fieldNames(node: JsonNode) = node.properties().map { it.key }.toSet()
    private fun writer(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden() }
    private fun reader(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden() }

    private companion object {
        val createFields = setOf("moduleId", "title", "description", "kind", "maxGrade", "deadline", "required", "allowLate", "questions")
        val editFields = createFields + setOf("expectedRevision", "confirmResetDrafts")
        val authoredFields = setOf("subject", "topic", "difficulty", "institution", "year", "statement", "options", "image", "correct", "explanation")
    }
}
