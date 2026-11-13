package br.com.ajadigital.core.ports.out



data class DashboardContent(val progress: List<Map<String, Any?>>, val completedMaterials: Int, val totalMaterials: Int, val resume: Map<String, Any?>?)
