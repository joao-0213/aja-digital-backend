package br.com.ajadigital.core.curriculum.domain.models

data class ReorderModulesRecordDto(val subjectId: String, val actorId: String, val role: String, val sessionHash: String, val expectedRevision: Int, val items: List<Pair<String, Int>>, val page: Int, val size: Int)
