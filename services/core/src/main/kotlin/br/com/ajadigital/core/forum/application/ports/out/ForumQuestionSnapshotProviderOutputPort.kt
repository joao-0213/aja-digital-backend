package br.com.ajadigital.core.forum.application.ports.out

import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshot
import br.com.ajadigital.core.forum.application.models.ForumQuestionSnapshotQuery

interface ForumQuestionSnapshotProviderOutputPort {
    fun snapshots(request: ForumQuestionSnapshotQuery): List<ForumQuestionSnapshot>
}
