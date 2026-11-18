package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.academic.domain.exceptions.AcademicNotFound
import br.com.ajadigital.core.identity.domain.contracts.AuthenticatedActor
import br.com.ajadigital.core.forum.application.ports.`in`.GetTopicInputPort
import br.com.ajadigital.core.forum.domain.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.application.models.GetTopicDto
import br.com.ajadigital.core.forum.domain.models.GetStoredTopicDto

class GetTopicUseCase(
    private val store: TopicStoreOutputPort,
) : GetTopicInputPort {
    override fun topic(request: GetTopicDto): Map<String, Any?> = request.let {
        val (actor, id) = request
        ForumPolicy.readable(actor).let { store.topic(GetStoredTopicDto(actor.id, actor.role, actor.sessionValidationReference, ForumPolicy.checkedId(id))) ?: throw AcademicNotFound() }
    }
}
