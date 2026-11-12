package br.com.ajadigital.questions.adapter.out.core

import br.com.ajadigital.questions.ports.out.CoreSessionIntrospection
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
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

@Component
class CoreSessionHttpClient(
    @Value("\${questions.core.internal-session-url}") sessionUrl: String,
    @Value("\${questions.core.service-token}") serviceToken: String,
    private val objectMapper: ObjectMapper,
) : CoreSessionIntrospection {
    private val serviceTokenBytes = serviceToken.toByteArray(Charsets.UTF_8)
    private val client = RestClient.builder().baseUrl(sessionUrl).requestFactory(
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(2))
            setReadTimeout(Duration.ofSeconds(2))
        },
    ).build()

    init {
        require(serviceTokenBytes.size >= 32) { "Questions service credential must contain at least 32 bytes" }
    }

    override fun requireActiveSession(cookie: String?) {
        if (cookie == null || !cookie.matches(Regex("[A-Za-z0-9_-]{43}"))) throw InvalidQuestionSession()
        try {
            val result = client.get().header("X-AJA-Service-Token", String(serviceTokenBytes, Charsets.UTF_8))
                .header(HttpHeaders.COOKIE, "aja_session=$cookie")
                .exchange { _, response ->
                    when (response.statusCode.value()) {
                        200 -> objectMapper.readValue(response.body, CoreSessionResponse::class.java)
                        401 -> throw InvalidQuestionSession()
                        else -> throw QuestionSessionUnavailable()
                    }
                }
            if (!result.active) throw InvalidQuestionSession()
            if (result.role !in setOf("student", "teacher", "admin")) throw QuestionAccessForbidden()
            return
        } catch (exception: InvalidQuestionSession) {
            throw exception
        } catch (exception: QuestionAccessForbidden) {
            throw exception
        } catch (exception: QuestionSessionUnavailable) {
            throw exception
        } catch (_: RestClientException) {
            throw QuestionSessionUnavailable()
        } catch (_: JacksonException) {
            throw QuestionSessionUnavailable()
        }
    }
}

private data class CoreSessionResponse(
    val role: String,
    val active: Boolean,
)
