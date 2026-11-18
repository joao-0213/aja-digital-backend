package br.com.ajadigital.core.curriculum.application.ports.`in`
import br.com.ajadigital.core.curriculum.application.models.*

import br.com.ajadigital.core.curriculum.application.models.ListSubjectsDto
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ListSubjectsInputPort {
    fun subjects(request: ListSubjectsDto): AcademicPage
}
