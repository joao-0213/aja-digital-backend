package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.forum.application.ports.out.ForumQuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshotQuery
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference
import br.com.ajadigital.core.forum.application.ports.`in`.CreateTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.forum.application.ports.out.ForumRequestFingerprintOutputPort

class CreateTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val questions: ForumQuestionSnapshotProviderOutputPort,
    private val fingerprints: ForumRequestFingerprintOutputPort,
) : CreateTopicInputPort {
    override fun create(request: CreateTopicCommand): ForumTopicView {
        val (actor, draft, key) = request
                ForumPolicy.writable(actor)
        require(key.length in 8..128 && ForumPolicy.validId(draft.classId) && ForumPolicy.validId(draft.subjectId))
        require(draft.title.isNotBlank() && draft.title.length <= 160 && draft.body.isNotBlank() && draft.body.length <= 10000)
        require(draft.tags.size <= 20 && draft.tags.all { it.isNotBlank() && it.length <= 40 } && draft.tags.distinct().size == draft.tags.size)
        require((draft.targetType == null) == (draft.targetId == null))
        val targetId = draft.targetId
        require(draft.targetType == null || draft.targetType in setOf("question", "material") && targetId != null && ForumPolicy.validId(targetId))
        require(draft.attachmentId == null || ForumPolicy.validId(draft.attachmentId))
        val hash = fingerprints.createTopic(draft)
        store.authorizeAndFindReplay(FindTopicReplayQuery(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash))?.let { return it }
        if (draft.targetType == "question") {
            val questionId = targetId ?: throw InvalidTopicReference()
            val snapshots = questions.snapshots(ForumQuestionSnapshotQuery(listOf(questionId)))
            if (snapshots.size != 1 || snapshots.single().questionId != questionId) throw InvalidTopicReference()
        }
        return store.create(CreateTopicRecord(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash))
    }
}
