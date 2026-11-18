package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.ListSubjectModulesQuery
import br.com.ajadigital.core.curriculum.application.models.Module

interface ListSubjectModulesInputPort {
    fun subjectModules(request: ListSubjectModulesQuery): List<Module>
}
