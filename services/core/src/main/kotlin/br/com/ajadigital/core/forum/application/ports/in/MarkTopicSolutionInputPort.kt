package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.MarkTopicSolutionDto

interface MarkTopicSolutionInputPort {
    fun solution(request: MarkTopicSolutionDto): Map<String, Any?>
}
