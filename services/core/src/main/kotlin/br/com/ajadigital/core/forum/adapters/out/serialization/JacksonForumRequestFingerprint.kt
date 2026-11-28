package br.com.ajadigital.core.forum.adapters.out.serialization

import br.com.ajadigital.core.forum.application.ports.out.ForumRequestFingerprintOutputPort
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

@Component
class JacksonForumRequestFingerprint(
    private val mapper: ObjectMapper,
) : ForumRequestFingerprintOutputPort {
    override fun createTopic(draft: TopicDraft): String = fingerprint(mapper.writeValueAsBytes(draft))

    override fun reply(body: String): String = fingerprint(mapper.writeValueAsBytes(mapOf("body" to body)))

    private fun fingerprint(bytes: ByteArray) = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
