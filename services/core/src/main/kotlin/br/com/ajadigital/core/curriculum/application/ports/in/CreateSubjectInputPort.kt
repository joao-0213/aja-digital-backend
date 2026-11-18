package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.CreateSubjectDto

interface CreateSubjectInputPort {
    fun createSubject(request: CreateSubjectDto): Map<String, Any?>
}
