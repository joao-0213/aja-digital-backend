package br.com.ajadigital.core.assessment.domain.policies

import br.com.ajadigital.core.shared.domain.models.SaveIdempotencyDto
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.assessment.domain.exceptions.QuestionsUnavailable
import br.com.ajadigital.core.assessment.domain.models.AssessmentDraft
import br.com.ajadigital.core.assessment.domain.models.AssessmentQuestion
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot
import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicForbidden
import br.com.ajadigital.core.classroom.domain.exceptions.AcademicIdempotencyConflict
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

internal object AssessmentPolicy {
    val createFields = setOf("moduleId", "title", "description", "kind", "maxGrade", "deadline", "required", "allowLate", "questions")
    val editFields = createFields + setOf("expectedRevision", "confirmResetDrafts")
    private val authoredFields = setOf("subject", "topic", "difficulty", "institution", "year", "statement", "options", "image", "correct", "explanation")

    fun draft(value: Any?): AssessmentDraft {
        val body = objectValue(value)
        require(fieldNames(body) == createFields)
        val moduleId = text(body, "moduleId").also(::id)
        val title = text(body, "title").trim().also { require(it.isNotEmpty() && it.length <= 160) }
        val description = text(body, "description").also { require(it.length <= 10000) }
        val kind = text(body, "kind").also { require(it in setOf("quiz", "work")) }
        val maxGrade = nullableNumber(body["maxGrade"])?.also { require(it > 0 && it.isFinite()) }
        val deadline = nullableInstant(body["deadline"])
        val required = bool(body, "required")
        val allowLate = bool(body, "allowLate")
        return AssessmentDraft(moduleId, title, description, kind, maxGrade, deadline, required, allowLate)
    }

    fun partialDraft(value: Any?, current: Map<String, Any?>): AssessmentDraft {
        val body = objectValue(value)
        require(fieldNames(body).all { it in editFields })
        val moduleId = if (body.containsKey("moduleId")) text(body, "moduleId").also(::id) else current["moduleId"] as String
        val title = if (body.containsKey("title")) text(body, "title").trim().also { require(it.isNotEmpty() && it.length <= 160) } else current["title"] as String
        val description = if (body.containsKey("description")) text(body, "description").also { require(it.length <= 10000) } else current["description"] as String
        val kind = if (body.containsKey("kind")) text(body, "kind").also { require(it in setOf("quiz", "work")) } else current["kind"] as String
        val maxGrade = if (body.containsKey("maxGrade")) nullableNumber(body["maxGrade"])?.also { require(it > 0 && it.isFinite()) } else current["maxGrade"] as Double?
        val deadline = if (body.containsKey("deadline")) nullableInstant(body["deadline"]) else current["deadline"]?.let { Instant.parse(it as String) }
        val required = if (body.containsKey("required")) bool(body, "required") else current["required"] as Boolean
        val allowLate = if (body.containsKey("allowLate")) bool(body, "allowLate") else current["allowLate"] as Boolean
        return AssessmentDraft(moduleId, title, description, kind, maxGrade, deadline, required, allowLate)
    }

    fun entries(value: Any?, kind: String, current: Map<String, Any?>?): List<AssessmentQuestion> {
        val items = value as? List<*> ?: throw IllegalArgumentException()
        require(items.size <= 100 && (kind != "work" || items.isEmpty()) && (kind != "quiz" || items.isNotEmpty()))
        val old = (current?.get("questions") as? List<*>)?.filterIsInstance<Map<*, *>>()?.associateBy { it["id"] as String }.orEmpty()
        val ids = mutableSetOf<String>()
        val clientKeys = mutableSetOf<String>()
        return items.map { rawEntry ->
            val entry = objectValue(rawEntry)
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
                    val requestedId = entry["questionId"]
                    if (requestedId != null) require(current != null && requestedId is String && old[requestedId]?.get("origin") == "author")
                    val questionId = if (requestedId == null) "author-${UUID.randomUUID()}" else (requestedId as String).also(::id)
                    require(ids.add(questionId))
                    val authored = entry["question"]
                    AssessmentQuestion(questionId, "author", authoredSnapshot(questionId, authored), 1, null)
                }
                else -> throw IllegalArgumentException()
            }
        }
    }

    fun capture(snapshots: QuestionSnapshotProviderOutputPort, entries: List<AssessmentQuestion>, existingIds: Set<String>): List<AssessmentQuestion> {
        val needed = entries.filter { it.source == "bank" && it.id !in existingIds }.map { it.id }
        if (needed.isEmpty()) return entries
        val snapshotsById = snapshots.snapshots(br.com.ajadigital.core.assessment.domain.models.QuestionSnapshotProviderSnapshotsDto(needed)).associateBy(BankQuestionSnapshot::questionId)
        if (snapshotsById.keys != needed.toSet()) throw QuestionsUnavailable()
        return entries.map { entry ->
            if (entry.source != "bank" || entry.id in existingIds) entry else {
                val snapshot = snapshotsById.getValue(entry.id)
                if (snapshot.revision < 1 || !snapshot.contentHash.matches(Regex("^[a-f0-9]{64}$")) || snapshot.options.size !in 2..20 || snapshot.correctOption !in snapshot.options.indices || snapshot.difficulty !in setOf("easy", "medium", "hard") || snapshot.image?.fileId?.let(::validId) == false) throw QuestionsUnavailable()
                entry.copy(snapshot = bankSnapshot(snapshot), sourceRevision = snapshot.revision, contentHash = snapshot.contentHash)
            }
        }
    }

    fun questionsChanged(current: Map<String, Any?>, desired: List<AssessmentQuestion>): Boolean {
        val old = (current["questions"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        return old.size != desired.size || desired.indices.any { index ->
            val prior = old[index]
            prior["id"] != desired[index].id || prior["origin"] != (if (desired[index].source == "bank") "bank" else "author") || desired[index].snapshot?.let { it != prior } == true
        }
    }

    private fun authoredSnapshot(id: String, value: Any?): Map<String, Any?> {
        val question = objectValue(value)
        require(fieldNames(question) == authoredFields)
        val subject = text(question, "subject"); val topic = text(question, "topic")
        val difficulty = text(question, "difficulty").also { require(it in setOf("easy", "medium", "hard")) }
        val institution = text(question, "institution")
        val year = intValue(question["year"])?.takeIf { it >= 0 } ?: throw IllegalArgumentException()
        val statement = text(question, "statement")
        val options = question["options"] as? List<*> ?: throw IllegalArgumentException()
        require(options.size in 2..20 && options.all { it is String })
        val textOptions = options.filterIsInstance<String>()
        val correct = intValue(question["correct"])?.takeIf { it in textOptions.indices } ?: throw IllegalArgumentException()
        val explanation = text(question, "explanation")
        val image = question["image"]?.let { imageValue ->
            val imageNode = objectValue(imageValue)
            require(fieldNames(imageNode) == setOf("src", "alt"))
            val src = text(imageNode, "src"); val alt = text(imageNode, "alt")
            require(src.matches(Regex("^/questions/[A-Za-z0-9_-]+\\.(?:svg|png|jpe?g|webp)$")))
            mapOf("src" to src, "alt" to alt)
        }
        return mapOf("id" to id, "origin" to "author", "subject" to subject, "topic" to topic, "difficulty" to difficulty, "institution" to institution, "year" to year, "statement" to statement, "options" to textOptions, "image" to image, "correct" to correct, "explanation" to explanation)
    }

    private fun bankSnapshot(snapshot: BankQuestionSnapshot) = mapOf(
        "id" to snapshot.questionId, "origin" to "bank", "subject" to snapshot.subject, "topic" to snapshot.topic,
        "difficulty" to snapshot.difficulty, "institution" to snapshot.institution, "year" to snapshot.year,
        "statement" to snapshot.statement, "options" to snapshot.options,
        "image" to snapshot.image?.let { mapOf("src" to "/api/v1/files/${it.fileId}/content", "alt" to it.alt) },
        "correct" to snapshot.correctOption, "explanation" to snapshot.explanation,
    )

    private fun objectValue(value: Any?): Map<*, *> = value as? Map<*, *> ?: throw IllegalArgumentException()
    fun isObject(value: Any?) = value is Map<*, *>
    private fun nullableNumber(value: Any?): Double? = when (value) { null -> null; is Number -> value.toDouble(); else -> throw IllegalArgumentException() }
    private fun nullableInstant(value: Any?): Instant? = when (value) {
        null -> null
        is String -> try { Instant.parse(value) } catch (_: DateTimeParseException) { throw IllegalArgumentException() }
        else -> throw IllegalArgumentException()
    }
    private fun intValue(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        else -> null
    }
    fun integer(node: Any?, key: String) = intValue((node as? Map<*, *>)?.get(key)) ?: throw IllegalArgumentException()
    fun value(node: Any?, key: String) = (node as? Map<*, *>)?.get(key)
    fun has(node: Any?, key: String) = (node as? Map<*, *>)?.containsKey(key) == true
    fun optionalBoolean(node: Any?, key: String): Boolean? {
        if (!has(node, key)) return null
        val value = value(node, key)
        return value as? Boolean ?: throw IllegalArgumentException()
    }
    private fun bool(node: Map<*, *>, key: String) = node[key] as? Boolean ?: throw IllegalArgumentException()
    private fun text(node: Map<*, *>, key: String) = node[key] as? String ?: throw IllegalArgumentException()
    fun id(value: String) = value.also { require(it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))) }
    fun validId(value: String) = value.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$"))
    fun validKey(value: String) { require(value.length in 8..128) }
    fun requestHash(mapper: JsonSerializerOutputPort, body: Any?) = MessageDigest.getInstance("SHA-256").digest(mapper.serialize(SerializeJsonDto(body))).joinToString("") { "%02x".format(it) }
    fun findReplay(idempotency: IdempotencyStoreOutputPort, actor: AcademicActor, operation: String, target: String, key: String, hash: String): Map<String, Any?>? =
        try { idempotency.find(FindIdempotencyDto(actor.id, operation, target, key, hash)) } catch (_: IdempotencyKeyReuse) { throw AcademicIdempotencyConflict() }
    fun fieldNames(value: Any?) = objectValue(value).keys.filterIsInstance<String>().toSet()
    fun writer(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher")) throw AcademicForbidden() }
    fun reader(actor: AcademicActor) { if (!actor.active || actor.role !in setOf("admin", "teacher", "student") || actor.role == "student" && actor.emailConfirmedAt == null) throw AcademicForbidden() }
}
