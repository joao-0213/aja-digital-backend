package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.CreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.contracts.Subject

interface CreateSubjectInputPort {
    fun createSubject(request: CreateSubjectCommand): Subject
}
