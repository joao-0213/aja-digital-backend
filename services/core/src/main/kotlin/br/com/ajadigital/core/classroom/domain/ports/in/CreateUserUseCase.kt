package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface CreateUserUseCase {
    fun createUser(actor: AcademicActor, name: String, emailInput: String, role: String, key: String): Map<String, Any?>
}
