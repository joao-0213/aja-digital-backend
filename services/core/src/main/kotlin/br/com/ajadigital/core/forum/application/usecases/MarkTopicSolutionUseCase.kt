package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.shared.domain.models.TransactionRunDto

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicSolutionInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.MarkTopicSolutionDto
import br.com.ajadigital.core.forum.domain.models.MarkTopicSolutionRecordDto
import br.com.ajadigital.core.forum.domain.models.GetStoredTopicDto
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort

class MarkTopicSolutionUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : MarkTopicSolutionInputPort {
    override fun solution(request: MarkTopicSolutionDto): Map<String, Any?> = transactionRunner.run(TransactionRunDto {
        val (actor, id, revision, replyId) = request
                ForumPolicy.writable(actor)
        require(revision > 0 && (replyId == null || ForumPolicy.validId(replyId)))
        val topicId = ForumPolicy.checkedId(id)
        store.solution(MarkTopicSolutionRecordDto(actor.id, actor.role, actor.sessionValidationReference, topicId, revision, replyId))
            ?: store.topic(GetStoredTopicDto(actor.id, actor.role, actor.sessionValidationReference, topicId))?.let { throw AcademicConflict(it["revision"] as? Int) }
            ?: throw AcademicNotFound()
    })
}
