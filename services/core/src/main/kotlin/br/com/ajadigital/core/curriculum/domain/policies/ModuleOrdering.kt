package br.com.ajadigital.core.curriculum.domain.policies

import br.com.ajadigital.core.curriculum.domain.models.ModuleRevision


object ModuleOrdering {
    fun accepts(requested: List<ModuleRevision>, current: List<ModuleRevision>): Boolean =
        requested.size == current.size && requested.toSet() == current.toSet()
}
