package br.com.ajadigital.core.materials.adapters.out.security

import br.com.ajadigital.core.materials.application.models.QuestionServiceCredential
import br.com.ajadigital.core.materials.application.ports.out.QuestionServiceAccessOutputPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class QuestionServiceAccess(
    @Value("\${core.internal.questions-token}") token: String,
) : QuestionServiceAccessOutputPort {
    private val expected = token.toByteArray()

    override fun allows(credential: QuestionServiceCredential): Boolean = MessageDigest.isEqual(expected, credential.value.orEmpty().toByteArray())
}
