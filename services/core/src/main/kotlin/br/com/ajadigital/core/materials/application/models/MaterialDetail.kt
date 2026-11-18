package br.com.ajadigital.core.materials.application.models

data class MaterialDetail(
    val material: Material,
    val module: MaterialModule,
    val subject: MaterialSubject,
    val canManageContent: Boolean,
)
