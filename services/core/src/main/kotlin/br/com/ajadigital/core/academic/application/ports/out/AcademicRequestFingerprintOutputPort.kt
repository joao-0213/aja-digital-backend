package br.com.ajadigital.core.academic.application.ports.out

interface AcademicRequestFingerprintOutputPort {
    fun createUser(name: String, email: String, role: String): String
    fun createClass(name: String, code: String, year: Int, shift: String): String
    fun createEnrollment(classId: String, email: String): String
    fun expectedRevision(revision: Int): String
}
