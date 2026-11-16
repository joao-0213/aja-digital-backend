package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface SetUserRoleUseCase {
    fun setRole(actor: AcademicActor, id: String, revision: Int, role: String): Map<String, Any?>
}
