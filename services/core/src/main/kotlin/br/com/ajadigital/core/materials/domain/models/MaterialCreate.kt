package br.com.ajadigital.core.materials.domain.models



data class MaterialCreate(val moduleId: String, val title: String, val kind: String, val duration: Double, val content: String, val fileId: String?, val videoUrl: String?)
