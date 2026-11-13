package br.com.ajadigital.core.ports.out



data class AcademicPage(val items: List<Map<String, Any?>>, val page: Int, val pageSize: Int, val total: Int)
