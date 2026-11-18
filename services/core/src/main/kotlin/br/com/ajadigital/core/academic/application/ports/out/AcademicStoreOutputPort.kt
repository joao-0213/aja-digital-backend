package br.com.ajadigital.core.academic.application.ports.out

import br.com.ajadigital.core.academic.application.models.*

interface AcademicStoreOutputPort {
    fun isCurrentAdminSession(request: AcademicStoreIsCurrentAdminSessionDto): Boolean
    fun listUsers(request: AcademicStoreListUsersDto): AcademicPage
    fun findUser(request: FindUserDto): Map<String, Any?>?
    fun createUser(request: AcademicStoreCreateUserDto): Map<String, Any?>
    fun updateUser(request: AcademicStoreUpdateUserDto): Map<String, Any?>?
    fun changeRole(request: AcademicStoreChangeRoleDto): Map<String, Any?>?
    fun deactivateUser(request: AcademicStoreDeactivateUserDto): Map<String, Any?>?
    fun withdrawUser(request: AcademicStoreWithdrawUserDto): Map<String, Any?>?
    fun listClasses(request: AcademicStoreListClassesDto): AcademicPage
    fun dashboardClasses(request: AcademicStoreDashboardClassesDto): AcademicPage
    fun findClass(request: AcademicStoreFindClassDto): Map<String, Any?>?
    fun createClass(request: AcademicStoreCreateClassDto): Map<String, Any?>
    fun updateClass(request: AcademicStoreUpdateClassDto): Map<String, Any?>?
    fun setClassActive(request: AcademicStoreSetClassActiveDto): Map<String, Any?>?
    fun listEnrollments(request: AcademicStoreListEnrollmentsDto): AcademicPage
    fun findEnrollment(request: AcademicStoreFindEnrollmentDto): Map<String, Any?>?
    fun createEnrollment(request: AcademicStoreCreateEnrollmentDto): Map<String, Any?>
    fun enrollmentPreview(request: AcademicStoreEnrollmentPreviewDto): Map<String, Any?>?
    fun transitionEnrollment(request: AcademicStoreTransitionEnrollmentDto): Map<String, Any?>?
    fun myEnrollments(request: AcademicStoreMyEnrollmentsDto): AcademicPage
    fun myPendingEnrollmentCount(request: CountPendingEnrollmentsForStudentDto): Int
    fun listStudents(request: AcademicStoreListStudentsDto): AcademicPage
    fun findStudent(request: AcademicStoreFindStudentDto): Map<String, Any?>?
}
