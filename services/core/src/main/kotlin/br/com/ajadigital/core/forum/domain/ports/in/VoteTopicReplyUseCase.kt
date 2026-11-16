package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface VoteTopicReplyUseCase {
    fun vote(actor: AcademicActor, id: String, replyId: String, voted: Boolean): Map<String, Any?>
}
