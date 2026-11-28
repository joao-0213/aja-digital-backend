package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.models.CreateSubjectCommand

interface CreateSubjectInputPort {
    fun createSubject(request: CreateSubjectCommand): Subject
}
