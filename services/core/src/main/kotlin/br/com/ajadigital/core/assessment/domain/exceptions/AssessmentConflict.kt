package br.com.ajadigital.core.assessment.domain.exceptions

class AssessmentConflict(val currentRevision: Int? = null) : RuntimeException()
