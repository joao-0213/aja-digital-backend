package br.com.ajadigital.core.assessment.application.ports.out

import br.com.ajadigital.core.assessment.application.models.QuestionSnapshotQuery
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot

interface QuestionSnapshotProviderOutputPort {
    fun snapshots(request: QuestionSnapshotQuery): List<BankQuestionSnapshot>
}
