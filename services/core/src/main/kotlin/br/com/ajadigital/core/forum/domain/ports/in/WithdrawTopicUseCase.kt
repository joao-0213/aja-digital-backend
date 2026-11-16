package br.com.ajadigital.core.forum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface WithdrawTopicUseCase {
    fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?>
}
