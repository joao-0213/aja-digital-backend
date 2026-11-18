package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.contracts.ListSubjectsQuery
import br.com.ajadigital.core.shared.application.contracts.Page
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary

interface ListSubjectsInputPort {
    fun subjects(request: ListSubjectsQuery): Page<SubjectSummary>
}
