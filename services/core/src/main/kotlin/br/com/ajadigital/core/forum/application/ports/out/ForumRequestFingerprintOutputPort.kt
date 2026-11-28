package br.com.ajadigital.core.forum.application.ports.out

import br.com.ajadigital.core.forum.domain.models.TopicDraft

interface ForumRequestFingerprintOutputPort {
    fun createTopic(draft: TopicDraft): String

    fun reply(body: String): String
}
