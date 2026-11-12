package br.com.ajadigital.core.ports.out

import java.time.Instant

data class AcademicPage(val items: List<Map<String, Any?>>, val page: Int, val pageSize: Int, val total: Int)
data class ProvisionedIdentity(
    val id: String, val actorId: String, val name: String, val email: String, val role: String, val now: Instant,
    val passwordHash: String,
    val passwordTokenId: String, val passwordTokenHash: String, val passwordEventId: String,
    val passwordCiphertext: ByteArray, val passwordNonce: ByteArray,
    val confirmationTokenId: String, val confirmationTokenHash: String, val confirmationEventId: String,
    val confirmationCiphertext: ByteArray, val confirmationNonce: ByteArray, val keyId: String,
)

interface AcademicStore {
    fun isCurrentAdminSession(actorId: String, sessionTokenHash: String, serializeAdminInvariant: Boolean): Boolean
    fun listUsers(page: Int, size: Int, sort: String, role: String?, active: Boolean?, query: String?): AcademicPage
    fun findUser(id: String): Map<String, Any?>?
    fun createUser(identity: ProvisionedIdentity): Map<String, Any?>
    fun updateUser(id: String, actorId: String, expectedRevision: Int, name: String?, email: String?): Map<String, Any?>?
    fun changeRole(id: String, actorId: String, expectedRevision: Int, role: String): Map<String, Any?>?
    fun deactivateUser(id: String, actorId: String, expectedRevision: Int): Map<String, Any?>?
    fun withdrawUser(id: String, actorId: String, expectedRevision: Int): Map<String, Any?>?
    fun listClasses(actorId: String, role: String, page: Int, size: Int, sort: String, active: Boolean?, query: String?): AcademicPage
    fun dashboardClasses(actorId: String, role: String, pageSize: Int): AcademicPage
    fun findClass(id: String, actorId: String, role: String): Map<String, Any?>?
    fun createClass(actorId: String, name: String, code: String, year: Int, shift: String): Map<String, Any?>
    fun updateClass(id: String, actorId: String, expectedRevision: Int, name: String?, code: String?, year: Int?, shift: String?): Map<String, Any?>?
    fun setClassActive(id: String, actorId: String, expectedRevision: Int, active: Boolean): Map<String, Any?>?
    fun listEnrollments(actorId: String, role: String, page: Int, size: Int, sort: String, classId: String?, studentId: String?, status: List<String>?, query: String?): AcademicPage
    fun findEnrollment(id: String): Map<String, Any?>?
    fun createEnrollment(actorId: String, classId: String, email: String): Map<String, Any?>
    fun enrollmentPreview(classId: String, email: String): Map<String, Any?>?
    fun transitionEnrollment(id: String, actorId: String, expectedRevision: Int, command: String): Map<String, Any?>?
    fun myEnrollments(studentId: String, page: Int, size: Int, sort: String): AcademicPage
    fun myPendingEnrollmentCount(studentId: String): Int
    fun listStudents(actorId: String, role: String, page: Int, size: Int, sort: String, classId: String?, query: String?, history: Boolean): AcademicPage
    fun findStudent(id: String, actorId: String, role: String): Map<String, Any?>?
}
