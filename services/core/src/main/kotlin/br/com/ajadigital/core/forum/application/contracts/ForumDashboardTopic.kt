package br.com.ajadigital.core.forum.application.contracts

data class ForumDashboardTopic(
    val id: String,
    val title: String,
    val subjectName: String,
    val replyCount: Int,
    val href: String,
)
