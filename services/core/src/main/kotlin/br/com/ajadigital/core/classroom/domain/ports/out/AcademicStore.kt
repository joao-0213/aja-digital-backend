package br.com.ajadigital.core.classroom.domain.ports.out



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
