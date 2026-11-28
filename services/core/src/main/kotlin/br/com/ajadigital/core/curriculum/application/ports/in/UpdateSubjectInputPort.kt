package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.models.UpdateSubjectCommand

interface UpdateSubjectInputPort {
    fun updateSubject(request: UpdateSubjectCommand): Subject
}
