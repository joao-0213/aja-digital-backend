package br.com.ajadigital.core.curriculum.adapters.out.serialization

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.models.CreateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.CreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.Module
import br.com.ajadigital.core.curriculum.application.ports.out.CurriculumReceiptCodecOutputPort
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest

@Component
class JacksonCurriculumReceiptCodec(
    private val mapper: ObjectMapper,
) : CurriculumReceiptCodecOutputPort {
    override fun fingerprint(request: CreateSubjectCommand): String = hash(
        mapOf(
            "classId" to request.classId,
            "name" to request.name,
            "teacherIds" to request.teacherIds,
            "color" to request.color,
            "icon" to request.icon,
            "description" to request.description,
        ),
    )

    override fun fingerprint(request: CreateModuleCommand): String = hash(
        mapOf("subjectId" to request.subjectId, "title" to request.title, "description" to request.description),
    )

    override fun encode(subject: Subject): String = mapper.writeValueAsString(subject)

    override fun encode(module: Module): String = mapper.writeValueAsString(module)

    override fun decodeSubject(response: String): Subject = mapper.readValue(response, Subject::class.java)

    override fun decodeModule(response: String): Module = mapper.readValue(response, Module::class.java)

    private fun hash(fields: Map<String, Any>): String = MessageDigest
        .getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(fields))
        .joinToString("") { "%02x".format(it) }
}
