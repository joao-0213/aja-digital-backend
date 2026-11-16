package br.com.ajadigital.core.assessment.domain.models



data class AssessmentPage(val items: List<Map<String, Any?>>, val page: Int, val pageSize: Int, val total: Long)
