package br.com.ajadigital.core.ports.out



interface QuestionSnapshotProvider {
    fun snapshots(ids: List<String>): List<BankQuestionSnapshot>
}
