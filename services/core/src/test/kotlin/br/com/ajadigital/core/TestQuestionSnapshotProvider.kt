package br.com.ajadigital.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import br.com.ajadigital.core.ports.out.BankQuestionSnapshot
import br.com.ajadigital.core.ports.out.BankQuestionImage
import br.com.ajadigital.core.ports.out.QuestionSnapshotProvider


class TestQuestionSnapshotProvider : QuestionSnapshotProvider {
    val requested = mutableListOf<List<String>>()
    var imageFileId = "question-image-1"
    private val nextGate = AtomicReference<Pair<CountDownLatch, CountDownLatch>?>(null)
    fun blockNextSnapshot(started: CountDownLatch, release: CountDownLatch) { nextGate.set(started to release) }
    override fun snapshots(ids: List<String>): List<BankQuestionSnapshot> {
        requested += ids
        nextGate.getAndSet(null)?.let { (started, release) ->
            started.countDown()
            check(release.await(20, TimeUnit.SECONDS)) { "Snapshot test gate was not released" }
        }
        if ("offline" in ids) throw br.com.ajadigital.core.application.academic.QuestionsUnavailable()
        return ids.map { id -> BankQuestionSnapshot(id, 3, "a".repeat(64), "Math", "Algebra", "easy", "AJA", 2026, "Original $id", listOf("one", "two"), 1, "Because", if (id == "bank-1") BankQuestionImage(imageFileId, "Figure") else null) }
    }
}
