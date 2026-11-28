package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.CreateTopicCommand
import br.com.ajadigital.core.forum.application.models.CreateTopicRecord
import br.com.ajadigital.core.forum.application.models.FindTopicReplayQuery
import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshotQuery
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.ports.`in`.CreateTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.ForumQuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.forum.application.ports.out.ForumRequestFingerprintOutputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference
import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class CreateTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val questions: ForumQuestionSnapshotProviderOutputPort,
    private val fingerprints: ForumRequestFingerprintOutputPort,
) : CreateTopicInputPort {
    override fun create(request: CreateTopicCommand): ForumTopicView {
        val (actor, draft, key) = request
        ForumPolicy.writable(actor.role, actor.active, actor.emailConfirmedAt != null)
        require(key.length in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH && ForumPolicy.validId(draft.classId) && ForumPolicy.validId(draft.subjectId))
        require(draft.title.isNotBlank() && draft.title.length <= MAX_TITLE_LENGTH && draft.body.isNotBlank() && draft.body.length <= MAX_TEXT_LENGTH)
        require(draft.tags.size <= MAX_TAGS && draft.tags.all { it.isNotBlank() && it.length <= MAX_TAG_LENGTH } && draft.tags.distinct().size == draft.tags.size)
        require((draft.targetType == null) == (draft.targetId == null))
        val targetId = draft.targetId
        require(draft.targetType == null || draft.targetType in setOf("question", "material") && targetId != null && ForumPolicy.validId(targetId))
        require(draft.attachmentId == null || ForumPolicy.validId(draft.attachmentId))
        val hash = fingerprints.createTopic(draft)
        store.authorizeAndFindReplay(FindTopicReplayQuery(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash))?.let { return it }
        if (draft.targetType == "question") verifyQuestionReference(targetId)
        return store.create(CreateTopicRecord(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash))
    }

    private fun verifyQuestionReference(targetId: String?) {
        val questionId = targetId ?: throw InvalidTopicReference()
        val snapshots = questions.snapshots(ForumQuestionSnapshotQuery(listOf(questionId)))
        if (snapshots.size != 1 || snapshots.single().questionId != questionId) throw InvalidTopicReference()
    }
}

private const val MIN_IDEMPOTENCY_KEY_LENGTH = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128
private const val MAX_TITLE_LENGTH = 160
private const val MAX_TEXT_LENGTH = 10000

private const val MAX_TAGS = 20

private const val MAX_TAG_LENGTH = 40
