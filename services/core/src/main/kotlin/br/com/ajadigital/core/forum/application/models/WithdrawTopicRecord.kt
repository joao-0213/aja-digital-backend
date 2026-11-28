package br.com.ajadigital.core.forum.application.models

data class WithdrawTopicRecord(
    val actorId: String,
    val role: String,
    val sessionHash: String,
    val id: String,
    val revision: Int,
)
