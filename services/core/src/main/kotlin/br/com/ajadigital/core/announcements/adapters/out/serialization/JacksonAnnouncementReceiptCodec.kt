package br.com.ajadigital.core.announcements.adapters.out.serialization

import br.com.ajadigital.core.announcements.application.contracts.Announcement
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementAuthor
import br.com.ajadigital.core.announcements.application.contracts.AnnouncementOccurrenceProjection
import br.com.ajadigital.core.announcements.application.ports.out.AnnouncementReceiptCodecOutputPort
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Component
class JacksonAnnouncementReceiptCodec(private val mapper: ObjectMapper) : AnnouncementReceiptCodecOutputPort {
    override fun encode(announcement: Announcement): String = mapper.writeValueAsString(announcement)
    override fun decode(receipt: String): Announcement {
        val stored = mapper.readValue(receipt, AnnouncementReceipt::class.java)
        val occurrence = stored.occurrence?.takeUnless(JsonNode::isNull)?.let {
            if (it.has("time")) AnnouncementOccurrenceProjection.WithTime(it.get("date").asString(), it.get("time")?.takeUnless(JsonNode::isNull)?.asString(), it.get("allDay").booleanValue())
            else AnnouncementOccurrenceProjection.WithoutTime(it.get("date").asString(), it.get("allDay").booleanValue())
        }
        return Announcement(stored.id, stored.revision, stored.createdAt, stored.updatedAt, stored.updatedBy, stored.classId, stored.subjectId, stored.title, stored.body, stored.notify, occurrence, stored.author)
    }

    private data class AnnouncementReceipt(
        val id: String,
        val revision: Int,
        val createdAt: String,
        val updatedAt: String,
        val updatedBy: String,
        val classId: String,
        val subjectId: String?,
        val title: String,
        val body: String,
        val notify: Boolean,
        val occurrence: JsonNode?,
        val author: AnnouncementAuthor,
    )
}
