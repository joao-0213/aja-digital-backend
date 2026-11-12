package br.com.ajadigital.core.ports.out

data class AnnouncementOccurrence(val date: String, val time: String?, val allDay: Boolean)

data class AnnouncementDraft(
    val classId: String,
    val subjectId: String?,
    val title: String,
    val body: String,
    val notify: Boolean,
    val occurrence: AnnouncementOccurrence?,
)

interface AnnouncementStore {
    fun authorizeCreate(actorId: String, role: String, sessionHash: String, draft: AnnouncementDraft)
    fun create(actorId: String, role: String, sessionHash: String, draft: AnnouncementDraft, key: String, requestHash: String): Map<String, Any?>
    fun update(actorId: String, role: String, sessionHash: String, id: String, revision: Int, draft: AnnouncementDraft): Map<String, Any?>?
    fun withdraw(actorId: String, role: String, sessionHash: String, id: String, revision: Int): Map<String, Any?>?
    fun announcement(actorId: String, role: String, sessionHash: String, id: String): Map<String, Any?>?
    fun announcements(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, classId: String?, subjectId: String?, query: String?): AcademicPage
    fun calendar(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, from: String?, to: String?, subjectId: String?): AcademicPage
    fun dashboardAnnouncements(actorId: String, role: String, sessionHash: String, size: Int): List<Map<String, Any?>>
    fun dashboardUpcoming(actorId: String, role: String, sessionHash: String, from: String, to: String, size: Int): List<Map<String, Any?>>
}
