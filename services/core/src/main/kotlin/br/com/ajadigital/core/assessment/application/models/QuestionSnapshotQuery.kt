package br.com.ajadigital.core.assessment.application.models

import java.util.Collections

class QuestionSnapshotQuery(ids: List<String>) {
    val ids: List<String> = Collections.unmodifiableList(ids.toList())
}
