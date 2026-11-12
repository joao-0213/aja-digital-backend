package br.com.ajadigital.questions

import br.com.ajadigital.questions.adapter.out.core.InvalidQuestionSession
import br.com.ajadigital.questions.adapter.out.core.QuestionAccessForbidden
import br.com.ajadigital.questions.adapter.out.core.QuestionSessionUnavailable
import br.com.ajadigital.questions.application.question.InvalidQuestionRequest
import br.com.ajadigital.questions.application.question.QuestionNotFound
import br.com.ajadigital.questions.application.question.ReadQuestionCatalog
import br.com.ajadigital.questions.domain.question.Question
import br.com.ajadigital.questions.domain.question.QuestionFilters
import br.com.ajadigital.questions.domain.question.QuestionSnapshot
import br.com.ajadigital.questions.ports.out.CoreSessionIntrospection
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

data class QuestionPracticeImage(val src: String, val alt: String)
data class QuestionPractice(
    val id: String,
    val subject: String,
    val topic: String,
    val difficulty: String,
    val institution: String,
    val year: Int,
    val statement: String,
    val options: List<String>,
    val image: QuestionPracticeImage?,
)
data class QuestionPracticePage(val items: List<QuestionPractice>, val page: Int, val pageSize: Int, val total: Long)
data class QuestionSnapshotsRequest(val ids: List<String>)
data class QuestionSnapshotsResponse(val snapshots: List<QuestionSnapshot>)

@RestController
class QuestionController(
    private val readQuestionCatalog: ReadQuestionCatalog,
    private val coreSessionIntrospection: CoreSessionIntrospection,
    @Value("\${questions.core.public-api-url}") publicCoreApiUrl: String,
) {
    private val fileContentBaseUrl = "${publicCoreApiUrl.trimEnd('/')}/files/"

    @GetMapping("/api/v1/questions")
    fun list(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) pageSize: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) subject: String?,
        @RequestParam(required = false) topic: String?,
        @RequestParam(required = false) difficulty: String?,
        @RequestParam(required = false) institution: String?,
        @RequestParam(required = false) year: String?,
        request: HttpServletRequest,
    ): QuestionPracticePage {
        coreSessionIntrospection.requireActiveSession(cookie)
        if (request.parameterMap.keys.any { it !in listQueryParameters }) throw InvalidQuestionRequest()
        val pageNumber = integer(page, 1, 1)
        val size = integer(pageSize, 20, 1, 100)
        val yearNumber = year?.let { integer(it, 0, 0) }
        if (difficulty != null && difficulty !in setOf("easy", "medium", "hard")) throw InvalidQuestionRequest()
        val result = readQuestionCatalog.list(QuestionFilters(q, subject, topic, difficulty, institution, yearNumber), pageNumber, size, sort ?: "id")
        return QuestionPracticePage(result.items.map { it.toPractice() }, result.page, result.pageSize, result.total)
    }

    @GetMapping("/api/v1/questions/{id}")
    fun get(
        @CookieValue(name = "aja_session", required = false) cookie: String?,
        @PathVariable id: String,
        request: HttpServletRequest,
    ): QuestionPractice {
        coreSessionIntrospection.requireActiveSession(cookie)
        if (request.parameterMap.isNotEmpty()) throw InvalidQuestionRequest()
        return readQuestionCatalog.get(id).toPractice()
    }

    private fun Question.toPractice() = QuestionPractice(
        id, subject, topic, difficulty, institution, year, statement, options,
        image?.let { QuestionPracticeImage("$fileContentBaseUrl${it.fileId}/content", it.alt) },
    )

    private fun integer(value: String?, default: Int, minimum: Int, maximum: Int = Int.MAX_VALUE): Int {
        if (value == null) return default
        val parsed = value.toIntOrNull() ?: throw InvalidQuestionRequest()
        if (parsed < minimum || parsed > maximum) throw InvalidQuestionRequest()
        return parsed
    }

    private companion object {
        val listQueryParameters = setOf("page", "pageSize", "sort", "q", "subject", "topic", "difficulty", "institution", "year")
    }
}

@RestController
class InternalQuestionSnapshotController(
    private val readQuestionCatalog: ReadQuestionCatalog,
    @Value("\${questions.core.service-token}") serviceToken: String,
) {
    private val expectedToken = serviceToken.toByteArray(StandardCharsets.UTF_8)

    init {
        require(expectedToken.size >= 32) { "Core service credential must contain at least 32 bytes" }
    }

    @PostMapping("/internal/v1/snapshots")
    fun snapshots(
        @RequestHeader(name = "X-AJA-Service-Token", required = false) candidateToken: String?,
        @RequestBody request: QuestionSnapshotsRequest,
    ): QuestionSnapshotsResponse {
        val supplied = candidateToken?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        if (!MessageDigest.isEqual(expectedToken, supplied)) throw InvalidQuestionsServiceCredential()
        return QuestionSnapshotsResponse(readQuestionCatalog.snapshots(request.ids))
    }
}

class InvalidQuestionsServiceCredential : RuntimeException()

@RestControllerAdvice
class QuestionsExceptionHandler {
    @ExceptionHandler(InvalidQuestionSession::class)
    fun unauthorized() = error(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED", "A valid session is required")

    @ExceptionHandler(QuestionAccessForbidden::class, InvalidQuestionsServiceCredential::class)
    fun forbidden() = error(HttpStatus.FORBIDDEN, "FORBIDDEN", "The operation is not allowed")

    @ExceptionHandler(QuestionNotFound::class)
    fun notFound() = error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource was not found")

    @ExceptionHandler(InvalidQuestionRequest::class, HttpMessageNotReadableException::class)
    fun invalid() = error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Request is invalid")

    @ExceptionHandler(QuestionSessionUnavailable::class)
    fun unavailable() = error(HttpStatus.SERVICE_UNAVAILABLE, "API_UNAVAILABLE", "Service dependencies are unavailable")

    @ExceptionHandler(DataAccessException::class)
    fun persistenceFailure() = error(HttpStatus.INTERNAL_SERVER_ERROR, "PERSISTENCE_FAILED", "Request could not be completed")

    private fun error(status: HttpStatus, code: String, message: String) = ResponseEntity.status(status).body(
        mapOf("error" to mapOf("code" to code, "message" to message, "requestId" to UUID.randomUUID().toString())),
    )
}
