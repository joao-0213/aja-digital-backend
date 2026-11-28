package br.com.ajadigital.core.forum.application.usecases
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.GetStoredTopicQuery
import br.com.ajadigital.core.forum.application.models.GetTopicQuery
import br.com.ajadigital.core.forum.application.ports.`in`.GetTopicInputPort
import br.com.ajadigital.core.forum.application.ports.out.TopicStoreOutputPort
import br.com.ajadigital.core.forum.domain.exceptions.ForumNotFound
import br.com.ajadigital.core.forum.domain.policies.ForumPolicy
import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor

class GetTopicUseCase(
    private val store: TopicStoreOutputPort,
) : GetTopicInputPort {
    override fun topic(request: GetTopicQuery): ForumTopicView = request.let {
        val (actor, id) = request
        ForumPolicy.readable(actor.role, actor.active, actor.emailConfirmedAt != null).let { store.topic(GetStoredTopicQuery(actor.id, actor.role, actor.sessionValidationReference, ForumPolicy.checkedId(id))) ?: throw ForumNotFound() }
    }
}
