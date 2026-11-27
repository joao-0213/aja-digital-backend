package br.com.ajadigital.core.announcements.domain.models

data class AnnouncementNotificationRequested(
    val recipient: String,
    val announcementId: String,
    val recipientId: String,
    val revision: Int,
    val title: String,
    val body: String,
)
