package br.com.ajadigital.core.academic.application.models

data class AcademicEnrollmentPreview(
    val email: String,
    val classId: String,
    val account: AcademicUserView?,
    val existingEnrollment: AcademicEnrollmentView?,
    val otherClass: AcademicClassReference?,
    val canEnroll: Boolean,
    val message: String?,
)
