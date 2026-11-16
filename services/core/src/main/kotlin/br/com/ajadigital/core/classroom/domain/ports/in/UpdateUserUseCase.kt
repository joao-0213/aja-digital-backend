package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface UpdateUserUseCase {
    fun updateUser(actor: AcademicActor, id: String, revision: Int, name: String?, email: String?): Map<String, Any?>
}
