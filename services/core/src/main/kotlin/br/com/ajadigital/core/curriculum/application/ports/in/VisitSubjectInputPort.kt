package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.RecordSubjectVisitCommand
import br.com.ajadigital.core.curriculum.application.models.SubjectVisit
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

interface VisitSubjectInputPort {
    fun readVisit(actor: AuthenticatedActor, subjectId: String): SubjectVisit
    fun recordVisit(command: RecordSubjectVisitCommand): SubjectVisit
}
