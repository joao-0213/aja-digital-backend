package br.com.ajadigital.core.ports.out



data class MaterialCreate(val moduleId: String, val title: String, val kind: String, val duration: Double, val content: String, val fileId: String?, val videoUrl: String?)
