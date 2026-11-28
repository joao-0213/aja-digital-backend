package br.com.ajadigital.core.materials.domain.policies

import br.com.ajadigital.core.materials.domain.exceptions.InvalidMaterialEdit
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsConflict
import br.com.ajadigital.core.materials.domain.exceptions.MaterialsNotFound
import br.com.ajadigital.core.materials.domain.models.MaterialRevision

internal object MaterialRules {
    private val identifier = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")

    fun validId(value: String): Boolean = identifier.matches(value)

    fun id(value: String): String = value.also { require(validId(it)) }

    fun matchesCurrentOrder(
        requested: List<MaterialRevision>,
        current: List<MaterialRevision>,
    ): Boolean = requested.size == current.size && requested.toSet() == current.toSet()

    fun requireDistinctFiles(files: List<String>) {
        if (files.toSet().size != files.size) throw MaterialsNotFound()
    }

    fun requireVideoEdit(
        isVideo: Boolean,
        videoUrl: String?,
    ) {
        if (!isVideo && videoUrl != null) throw InvalidMaterialEdit()
    }

    fun requireCurrentRevision(
        actual: Int,
        expected: Int,
    ) {
        if (actual != expected) throw MaterialsConflict()
    }
}
