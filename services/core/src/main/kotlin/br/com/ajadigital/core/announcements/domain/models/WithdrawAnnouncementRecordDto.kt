package br.com.ajadigital.core.announcements.domain.models

data class WithdrawAnnouncementRecordDto(val actorId: String, val role: String, val sessionHash: String, val id: String, val revision: Int)
