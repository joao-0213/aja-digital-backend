package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.assessment.application.ports.out.QuestionSnapshotProviderOutputPort
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.domain.models.*
import br.com.ajadigital.core.forum.application.ports.`in`.CreateTopicInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort
import br.com.ajadigital.core.shared.domain.models.SerializeJsonDto

class CreateTopicUseCase(
    private val store: TopicStoreOutputPort,
    private val questions: QuestionSnapshotProviderOutputPort,
    private val mapper: JsonSerializerOutputPort,
) : CreateTopicInputPort {
    override fun create(request: CreateTopicDto): Map<String, Any?> {
        val (actor, draft, key) = request
                ForumPolicy.writable(actor)
        require(key.length in 8..128 && ForumPolicy.validId(draft.classId) && ForumPolicy.validId(draft.subjectId))
        require(draft.title.isNotBlank() && draft.title.length <= 160 && draft.body.isNotBlank() && draft.body.length <= 10000)
        require(draft.tags.size <= 20 && draft.tags.all { it.isNotBlank() && it.length <= 40 } && draft.tags.distinct().size == draft.tags.size)
        require((draft.targetType == null) == (draft.targetId == null))
        require(draft.targetType == null || draft.targetType in setOf("question", "material") && ForumPolicy.validId(draft.targetId!!))
        require(draft.attachmentId == null || ForumPolicy.validId(draft.attachmentId))
        val hash = ForumPolicy.sha256(mapper.serialize(SerializeJsonDto(draft)))
        store.authorizeAndFindReplay(AuthorizeAndFindTopicReplayDto(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash))?.let { return it }
        if (draft.targetType == "question") {
            val snapshots = questions.snapshots(br.com.ajadigital.core.assessment.application.models.QuestionSnapshotQuery(listOf(draft.targetId!!)))
            if (snapshots.size != 1 || snapshots.single().questionId != draft.targetId) throw InvalidTopicReference()
        }
        return store.create(CreateTopicRecordDto(actor.id, actor.role, actor.sessionValidationReference, draft, key, hash))
    }
}
