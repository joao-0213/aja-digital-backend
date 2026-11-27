package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*


interface MarkTopicSolutionInputPort {
    fun solution(request: MarkTopicSolutionCommand): ForumTopicView
}
