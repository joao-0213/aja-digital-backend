package br.com.ajadigital.core.announcements.domain.exceptions

class AnnouncementConflict(val currentRevision: Int? = null) : RuntimeException()
