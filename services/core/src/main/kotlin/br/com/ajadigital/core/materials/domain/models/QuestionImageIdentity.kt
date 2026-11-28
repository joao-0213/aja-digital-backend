package br.com.ajadigital.core.materials.domain.models

import br.com.ajadigital.core.materials.domain.exceptions.MaterialsConflict

data class QuestionImageIdentity(
    val questionId: String,
    val name: String,
    val mime: String,
    val sha256: String,
) {
    fun verifyReplay(stored: QuestionImageIdentity?) {
        if (stored != this) throw MaterialsConflict()
    }
}
