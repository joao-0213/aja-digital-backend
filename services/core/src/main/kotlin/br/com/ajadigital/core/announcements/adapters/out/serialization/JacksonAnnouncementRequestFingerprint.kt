package br.com.ajadigital.core.announcements.adapters.out.serialization

import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementRequestFingerprintOutputPort
import br.com.ajadigital.core.announcements.domain.models.AnnouncementDraft
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

@Component
class JacksonAnnouncementRequestFingerprint(
    private val mapper: ObjectMapper,
) : AnnouncementRequestFingerprintOutputPort {
    override fun create(draft: AnnouncementDraft): String = MessageDigest
        .getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(draft))
        .joinToString("") { "%02x".format(it) }
}
