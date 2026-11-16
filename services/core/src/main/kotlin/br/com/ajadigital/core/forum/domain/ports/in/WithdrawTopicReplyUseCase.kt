package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface WithdrawTopicReplyUseCase {
    fun withdrawReply(actor: AcademicActor, id: String, replyId: String, revision: Int): Map<String, Any?>
}
