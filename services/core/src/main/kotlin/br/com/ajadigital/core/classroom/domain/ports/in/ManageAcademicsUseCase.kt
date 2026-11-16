package br.com.ajadigital.core.classroom.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ManageAcademicsUseCase {
    fun users(actor: AcademicActor, page: Int, size: Int, sort: String, role: String?, active: Boolean?, q: String?): AcademicPage
    fun user(actor: AcademicActor, id: String): Map<String, Any?>
    fun createUser(actor: AcademicActor, name: String, emailInput: String, role: String, key: String): Map<String, Any?>
    fun updateUser(actor: AcademicActor, id: String, revision: Int, name: String?, email: String?): Map<String, Any?>
    fun setRole(actor: AcademicActor, id: String, revision: Int, role: String): Map<String, Any?>
    fun deactivate(actor: AcademicActor, id: String, revision: Int): Map<String, Any?>
    fun withdraw(actor: AcademicActor, id: String, revision: Int): Map<String, Any?>
    fun classes(actor: AcademicActor, page: Int, size: Int, sort: String, active: Boolean?, q: String?): AcademicPage
    fun dashboardClasses(actor: AcademicActor, pageSize: Int = 100): AcademicPage
    fun classById(actor: AcademicActor, id: String): Map<String, Any?>
    fun createClass(actor: AcademicActor, name: String, code: String, year: Int, shift: String, key: String): Map<String, Any?>
    fun updateClass(actor: AcademicActor, id: String, revision: Int, name: String?, code: String?, year: Int?, shift: String?): Map<String, Any?>
    fun closeClass(actor: AcademicActor, id: String, revision: Int, key: String): Map<String, Any?>
    fun reopenClass(actor: AcademicActor, id: String, revision: Int, key: String): Map<String, Any?>
    fun enrollments(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, studentId: String?, status: List<String>?, q: String?): AcademicPage
    fun enrollment(actor: AcademicActor, id: String): Map<String, Any?>
    fun preview(actor: AcademicActor, classId: String, email: String): Map<String, Any?>
    fun createEnrollment(actor: AcademicActor, classId: String, emailInput: String, key: String): Map<String, Any?>
    fun transitionEnrollment(actor: AcademicActor, id: String, revision: Int, command: String, key: String): Map<String, Any?>
    fun myEnrollments(actor: AcademicActor, page: Int, size: Int, sort: String): AcademicPage
    fun myPendingEnrollmentCount(actor: AcademicActor): Int
    fun students(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, q: String?, history: Boolean): AcademicPage
    fun student(actor: AcademicActor, id: String): Map<String, Any?>
}
