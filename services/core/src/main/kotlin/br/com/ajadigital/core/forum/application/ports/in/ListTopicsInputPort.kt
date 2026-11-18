package br.com.ajadigital.core.forum.application.ports.`in`
import br.com.ajadigital.core.forum.application.models.*

import br.com.ajadigital.core.forum.application.models.ListTopicsDto

interface ListTopicsInputPort {
    fun topics(request: ListTopicsDto): Map<String, Any?>
}
