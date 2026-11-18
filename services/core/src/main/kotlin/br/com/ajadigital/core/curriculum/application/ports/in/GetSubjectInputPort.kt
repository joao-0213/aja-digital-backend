package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.GetSubjectQuery
import br.com.ajadigital.core.curriculum.application.contracts.SubjectSummary

interface GetSubjectInputPort {
    fun subject(request: GetSubjectQuery): SubjectSummary
}
