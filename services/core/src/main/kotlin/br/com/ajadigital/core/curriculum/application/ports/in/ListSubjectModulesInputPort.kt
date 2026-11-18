package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.ListSubjectModulesDto

interface ListSubjectModulesInputPort {
    fun subjectModules(request: ListSubjectModulesDto): List<Map<String, Any?>>
}
