package br.com.ajadigital.questions.adapter.out.core

import br.com.ajadigital.questions.ports.out.CoreSessionIntrospection
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant

class InvalidQuestionSession : RuntimeException()

class QuestionSessionUnavailable : RuntimeException()

class QuestionAccessForbidden : RuntimeException()

class QuestionPracticeBlocked : RuntimeException()

@Component
class CoreSessionHttpClient(
    @Value("\${questions.core.internal-session-url}") sessionUrl: String,
    @Value("\${questions.core.service-token}") serviceToken: String,
    private val objectMapper: ObjectMapper,
) : CoreSessionIntrospection {
    private val serviceTokenBytes = serviceToken.toByteArray(Charsets.UTF_8)
    private val client =
        RestClient
            .builder()
            .baseUrl(sessionUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(Duration.ofSeconds(2))
                    setReadTimeout(Duration.ofSeconds(2))
                },
            ).build()

    init {
        require(serviceTokenBytes.size >= MIN_SERVICE_TOKEN_BYTES) { "Questions service credential must contain at least 32 bytes" }
    }

    override fun requireActiveSession(cookie: String?) {
        if (cookie == null || !cookie.matches(Regex("[A-Za-z0-9_-]{43}"))) throw InvalidQuestionSession()
        callCore {
            val result =
                client
                    .get()
                    .header("X-AJA-Service-Token", String(serviceTokenBytes, Charsets.UTF_8))
                    .header(HttpHeaders.COOKIE, "aja_session=$cookie")
                    .exchange { _, response ->
                        when (response.statusCode.value()) {
                            HttpStatus.OK.value() -> objectMapper.readValue(response.body, CoreSessionResponse::class.java)
                            HttpStatus.UNAUTHORIZED.value() -> throw InvalidQuestionSession()
                            else -> throw QuestionSessionUnavailable()
                        }
                    }
            if (!result.active) throw InvalidQuestionSession()
            if (result.role !in setOf("student", "teacher", "admin")) throw QuestionAccessForbidden()
        }
    }

    override fun practiceBlocked(
        cookie: String?,
        questionId: String,
    ): Boolean {
        if (cookie == null || !cookie.matches(Regex("[A-Za-z0-9_-]{43}"))) throw InvalidQuestionSession()
        return callCore {
            client
                .post()
                .uri("/practice")
                .header("X-AJA-Service-Token", String(serviceTokenBytes, Charsets.UTF_8))
                .header(HttpHeaders.COOKIE, "aja_session=$cookie")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("questionId" to questionId))
                .exchange { _, response ->
                    when (response.statusCode.value()) {
                        HttpStatus.OK.value() -> objectMapper.readValue(response.body, CorePracticeResponse::class.java).blocked
                        HttpStatus.UNAUTHORIZED.value() -> throw InvalidQuestionSession()
                        HttpStatus.FORBIDDEN.value() -> throw QuestionAccessForbidden()
                        else -> throw QuestionSessionUnavailable()
                    }
                }
        }
    }

    private fun <T> callCore(block: () -> T): T = try {
        block()
    } catch (_: RestClientException) {
        throw QuestionSessionUnavailable()
    } catch (_: JacksonException) {
        throw QuestionSessionUnavailable()
    }
}

private data class CoreSessionResponse(
    val role: String,
    val active: Boolean,
)

private data class CorePracticeResponse(
    val blocked: Boolean,
)

private const val MIN_SERVICE_TOKEN_BYTES = 32
