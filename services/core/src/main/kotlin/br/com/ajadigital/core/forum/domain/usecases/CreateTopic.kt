package br.com.ajadigital.core.forum.domain.usecases

import br.com.ajadigital.core.assessment.domain.ports.out.QuestionSnapshotProvider
import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.forum.domain.exceptions.InvalidTopicReference
import br.com.ajadigital.core.forum.domain.models.TopicDraft
import br.com.ajadigital.core.forum.domain.ports.`in`.CreateTopicUseCase
import br.com.ajadigital.core.forum.domain.ports.out.TopicStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import tools.jackson.databind.ObjectMapper

class CreateTopic(
    private val store: TopicStore,
    private val questions: QuestionSnapshotProvider,
    private val mapper: ObjectMapper,
) : CreateTopicUseCase {
    override fun create(actor: AcademicActor, draft: TopicDraft, key: String): Map<String, Any?> {
        ForumSupport.writable(actor)
        require(key.length in 8..128 && ForumSupport.validId(draft.classId) && ForumSupport.validId(draft.subjectId))
        require(draft.title.isNotBlank() && draft.title.length <= 160 && draft.body.isNotBlank() && draft.body.length <= 10000)
        require(draft.tags.size <= 20 && draft.tags.all { it.isNotBlank() && it.length <= 40 } && draft.tags.distinct().size == draft.tags.size)
        require((draft.targetType == null) == (draft.targetId == null))
        require(draft.targetType == null || draft.targetType in setOf("question", "material") && ForumSupport.validId(draft.targetId!!))
        require(draft.attachmentId == null || ForumSupport.validId(draft.attachmentId))
        val hash = ForumSupport.sha256(mapper.writeValueAsBytes(draft))
        store.authorizeAndFindReplay(actor.id, actor.role, actor.sessionTokenHash, draft, key, hash)?.let { return it }
        if (draft.targetType == "question") {
            val snapshots = questions.snapshots(listOf(draft.targetId!!))
            if (snapshots.size != 1 || snapshots.single().questionId != draft.targetId) throw InvalidTopicReference()
        }
        return store.create(actor.id, actor.role, actor.sessionTokenHash, draft, key, hash)
    }
}
