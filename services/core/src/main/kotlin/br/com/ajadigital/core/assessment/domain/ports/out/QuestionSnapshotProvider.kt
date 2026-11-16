package br.com.ajadigital.core.assessment.domain.ports.out
import br.com.ajadigital.core.assessment.domain.models.BankQuestionSnapshot



interface QuestionSnapshotProvider {
    fun snapshots(ids: List<String>): List<BankQuestionSnapshot>
}
