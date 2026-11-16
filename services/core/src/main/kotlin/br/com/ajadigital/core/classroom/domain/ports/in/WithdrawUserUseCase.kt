package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface WithdrawUserUseCase {
    fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?>
}
