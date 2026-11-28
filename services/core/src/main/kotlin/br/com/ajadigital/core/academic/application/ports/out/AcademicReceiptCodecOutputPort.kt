package br.com.ajadigital.core.academic.application.ports.out

import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.AcademicUserView

interface AcademicReceiptCodecOutputPort {
    fun encode(value: AcademicUserView): String

    fun decodeUser(value: String): AcademicUserView

    fun encode(value: AcademicClassView): String

    fun decodeClass(value: String): AcademicClassView

    fun encode(value: AcademicEnrollmentView): String

    fun decodeEnrollment(value: String): AcademicEnrollmentView
}
