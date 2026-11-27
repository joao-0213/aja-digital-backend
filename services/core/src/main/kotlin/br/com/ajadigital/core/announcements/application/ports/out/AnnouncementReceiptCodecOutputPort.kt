package br.com.ajadigital.core.announcements.application.ports.out

import br.com.ajadigital.core.announcements.application.contracts.Announcement

interface AnnouncementReceiptCodecOutputPort {
    fun encode(announcement: Announcement): String
    fun decode(receipt: String): Announcement
}
