package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor

interface ListSubjectModulesUseCase {
    fun subjectModules(actor: AcademicActor, subjectId: String): List<Map<String, Any?>>
}
