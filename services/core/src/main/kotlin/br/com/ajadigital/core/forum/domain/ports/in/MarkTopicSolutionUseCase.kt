package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface MarkTopicSolutionUseCase {
    fun solution(actor: AcademicActor, id: String, revision: Int, replyId: String?): Map<String, Any?>
}
