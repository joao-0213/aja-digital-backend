package br.com.ajadigital.core.forum.adapters.out.persistence

import java.time.Instant

internal data class ForumTopicLock(
    val id: String,
    val revision: Int,
    val authorId: String,
    val classId: String,
    val subjectId: String,
    val solutionReplyId: String?,
    val lastActivityAt: Instant,
)
