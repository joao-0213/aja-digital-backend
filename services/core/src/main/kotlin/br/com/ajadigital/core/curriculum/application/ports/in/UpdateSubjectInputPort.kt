package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.UpdateSubjectCommand
import br.com.ajadigital.core.curriculum.application.contracts.Subject

interface UpdateSubjectInputPort {
    fun updateSubject(request: UpdateSubjectCommand): Subject
}
