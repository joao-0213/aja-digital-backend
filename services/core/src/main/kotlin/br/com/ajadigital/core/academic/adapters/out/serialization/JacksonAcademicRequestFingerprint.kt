package br.com.ajadigital.core.academic.adapters.out.serialization

import br.com.ajadigital.core.academic.application.ports.out.AcademicRequestFingerprintOutputPort
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

@Component
class JacksonAcademicRequestFingerprint(
    private val mapper: ObjectMapper,
) : AcademicRequestFingerprintOutputPort {
    override fun createUser(
        name: String,
        email: String,
        role: String,
    ) = fingerprint(mapOf("name" to name, "email" to email, "role" to role))

    override fun createClass(
        name: String,
        code: String,
        year: Int,
        shift: String,
    ) = fingerprint(
        mapOf(
            "name" to name,
            "code" to code,
            "year" to year,
            "shift" to shift,
        ),
    )

    override fun createEnrollment(
        classId: String,
        email: String,
    ) = fingerprint(mapOf("classId" to classId, "email" to email))

    override fun expectedRevision(revision: Int) = fingerprint(mapOf("expectedRevision" to revision))

    private fun fingerprint(value: Any): String = MessageDigest
        .getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(value))
        .joinToString("") { "%02x".format(it) }
}
