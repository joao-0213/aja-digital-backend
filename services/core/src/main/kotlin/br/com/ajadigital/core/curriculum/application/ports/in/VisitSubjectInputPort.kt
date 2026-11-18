package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.VisitSubjectDto

interface VisitSubjectInputPort {
    fun visit(request: VisitSubjectDto): Map<String, Any?>
}
