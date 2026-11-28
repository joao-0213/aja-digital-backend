package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy

import br.com.ajadigital.core.academic.domain.exceptions.AcademicConflict
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.MarkTopicSolutionInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.MarkTopicSolutionCommand
import br.com.ajadigital.core.forum.application.models.MarkTopicSolutionRecord
import br.com.ajadigital.core.forum.application.models.GetStoredTopicQuery
import br.com.ajadigital.core.shared.application.ports.out.TransactionRunnerOutputPort

class MarkTopicSolutionUseCase(
    private val store: TopicStoreOutputPort,
    private val transactionRunner: TransactionRunnerOutputPort,
) : MarkTopicSolutionInputPort {
    override fun solution(request: MarkTopicSolutionCommand): ForumTopicView = transactionRunner.run {
        val (actor, id, revision, replyId) = request
                ForumPolicy.writable(actor)
        require(revision > 0 && (replyId == null || ForumPolicy.validId(replyId)))
        val topicId = ForumPolicy.checkedId(id)
        store.solution(MarkTopicSolutionRecord(actor.id, actor.role, actor.sessionValidationReference, topicId, revision, replyId))
            ?: store.topic(GetStoredTopicQuery(actor.id, actor.role, actor.sessionValidationReference, topicId))?.let { throw AcademicConflict(it.revision) }
            ?: throw AcademicNotFound()
    }
}
