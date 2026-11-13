package br.com.ajadigital.core.ports.out



data class SubmissionPage(val items: List<Map<String, Any?>>, val page: Int, val pageSize: Int, val total: Long)
