package br.com.ajadigital.core.identity.application.ports.`in`

import br.com.ajadigital.core.identity.application.models.ResolveStudentSessionDto
import br.com.ajadigital.core.identity.domain.models.SessionRecord

interface ResolveSessionInputPort {
    fun resolve(request: ResolveStudentSessionDto): SessionRecord
}
