package br.com.ajadigital.core.materials.application.ports.out

import br.com.ajadigital.core.materials.application.models.QuestionServiceCredential

fun interface QuestionServiceAccessOutputPort {
    fun allows(credential: QuestionServiceCredential): Boolean
}
