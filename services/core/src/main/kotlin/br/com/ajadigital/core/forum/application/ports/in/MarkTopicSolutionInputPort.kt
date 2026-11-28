package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.ForumTopicView
import br.com.ajadigital.core.forum.application.models.MarkTopicSolutionCommand

interface MarkTopicSolutionInputPort {
    fun solution(request: MarkTopicSolutionCommand): ForumTopicView
}
