package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.UpdateSubjectDto

interface UpdateSubjectInputPort {
    fun updateSubject(request: UpdateSubjectDto): Map<String, Any?>
}
