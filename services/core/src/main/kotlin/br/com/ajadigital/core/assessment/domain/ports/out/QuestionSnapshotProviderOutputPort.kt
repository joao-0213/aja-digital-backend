package br.com.ajadigital.core.assessment.domain.ports.out

import br.com.ajadigital.core.assessment.domain.models.*

interface QuestionSnapshotProviderOutputPort {
    fun snapshots(request: QuestionSnapshotProviderSnapshotsDto): List<BankQuestionSnapshot>
}
