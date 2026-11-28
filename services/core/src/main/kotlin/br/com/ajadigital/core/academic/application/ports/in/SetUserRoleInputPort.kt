package br.com.ajadigital.core.academic.application.ports.`in`
import br.com.ajadigital.core.academic.application.models.AcademicUserView
import br.com.ajadigital.core.academic.application.models.SetUserRoleDto
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface SetUserRoleInputPort {
    fun setRole(request: SetUserRoleDto): AcademicUserView
}
