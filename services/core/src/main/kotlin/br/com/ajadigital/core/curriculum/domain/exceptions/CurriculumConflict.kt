package br.com.ajadigital.core.curriculum.domain.exceptions

class CurriculumConflict(val currentRevision: Int? = null) : RuntimeException()
