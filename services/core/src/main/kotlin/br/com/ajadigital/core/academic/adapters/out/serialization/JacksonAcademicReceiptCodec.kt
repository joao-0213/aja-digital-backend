package br.com.ajadigital.core.academic.adapters.out.serialization

import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.AcademicUserView
import br.com.ajadigital.core.academic.application.ports.out.AcademicReceiptCodecOutputPort
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class JacksonAcademicReceiptCodec(
    private val mapper: ObjectMapper,
) : AcademicReceiptCodecOutputPort {
    override fun encode(value: AcademicUserView) = mapper.writeValueAsString(value)

    override fun decodeUser(value: String) = mapper.readValue(value, AcademicUserView::class.java)

    override fun encode(value: AcademicClassView) = mapper.writeValueAsString(value)

    override fun decodeClass(value: String) = mapper.readValue(value, AcademicClassView::class.java)

    override fun encode(value: AcademicEnrollmentView) = mapper.writeValueAsString(
        linkedMapOf(
            "id" to value.id,
            "revision" to value.revision,
            "createdAt" to value.createdAt,
            "updatedAt" to value.updatedAt,
            "updatedBy" to value.updatedBy,
            "classId" to value.classId,
            "email" to value.email,
            "studentId" to value.studentId,
            "status" to value.status,
            "createdBy" to value.createdBy,
            "enrolledAt" to value.enrolledAt,
            "endedAt" to value.endedAt,
            "legacy" to value.legacy,
            "unassignedAssessmentIds" to value.unassignedAssessmentIds,
            "className" to value.className,
        ),
    )

    override fun decodeEnrollment(value: String) = mapper.readValue(value, AcademicEnrollmentView::class.java)
}
