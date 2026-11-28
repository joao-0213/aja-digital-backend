package br.com.ajadigital.core.forum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

data class ListTopicsQuery(
    val actor: AuthenticatedActor,
    val page: Int,
    val size: Int,
    val sort: String,
    val classId: String?,
    val subjectId: String?,
    val query: String?,
    val unread: Boolean?,
    val solved: Boolean?,
)
