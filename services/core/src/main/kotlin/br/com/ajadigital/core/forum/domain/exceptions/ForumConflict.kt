package br.com.ajadigital.core.forum.domain.exceptions

class ForumConflict(val currentRevision: Int? = null) : RuntimeException()
