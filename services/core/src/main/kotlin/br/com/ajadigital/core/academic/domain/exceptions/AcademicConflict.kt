package br.com.ajadigital.core.academic.domain.exceptions



class AcademicConflict(val currentRevision: Int? = null) : RuntimeException()
