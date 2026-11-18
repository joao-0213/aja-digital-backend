package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.GetSubjectDto

interface GetSubjectInputPort {
    fun subject(request: GetSubjectDto): Map<String, Any?>
}
