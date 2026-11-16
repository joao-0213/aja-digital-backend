package br.com.ajadigital.core.classroom.domain.ports.out



data class AcademicPage(val items: List<Map<String, Any?>>, val page: Int, val pageSize: Int, val total: Int)
