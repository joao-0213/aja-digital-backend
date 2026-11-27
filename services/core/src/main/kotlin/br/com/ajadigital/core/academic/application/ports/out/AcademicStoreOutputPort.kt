package br.com.ajadigital.core.academic.application.ports.out

import br.com.ajadigital.core.academic.application.models.*
import br.com.ajadigital.core.academic.application.contracts.DashboardClassProjection
import br.com.ajadigital.core.shared.application.contracts.Page

interface AcademicStoreOutputPort {
    fun isCurrentAdminSession(request: AcademicStoreIsCurrentAdminSessionDto): Boolean
    fun listUsers(request: AcademicStoreListUsersDto): Page<AcademicUserView>
    fun findUser(request: FindUserDto): AcademicUserView?
    fun createUser(request: AcademicStoreCreateUserDto): AcademicUserView
    fun updateUser(request: AcademicStoreUpdateUserDto): AcademicUserView?
    fun changeRole(request: AcademicStoreChangeRoleDto): AcademicUserView?
    fun deactivateUser(request: AcademicStoreDeactivateUserDto): AcademicUserView?
    fun withdrawUser(request: AcademicStoreWithdrawUserDto): AcademicUserView?
    fun listClasses(request: AcademicStoreListClassesDto): Page<AcademicClassView>
    fun dashboardClasses(request: AcademicStoreDashboardClassesDto): List<DashboardClassProjection>
    fun findClass(request: AcademicStoreFindClassDto): AcademicClassView?
    fun createClass(request: AcademicStoreCreateClassDto): AcademicClassView
    fun updateClass(request: AcademicStoreUpdateClassDto): AcademicClassView?
    fun setClassActive(request: AcademicStoreSetClassActiveDto): AcademicClassView?
    fun listEnrollments(request: AcademicStoreListEnrollmentsDto): Page<AcademicEnrollmentView>
    fun findEnrollment(request: AcademicStoreFindEnrollmentDto): AcademicEnrollmentView?
    fun createEnrollment(request: AcademicStoreCreateEnrollmentDto): AcademicEnrollmentView
    fun enrollmentPreview(request: AcademicStoreEnrollmentPreviewDto): AcademicEnrollmentPreview?
    fun transitionEnrollment(request: AcademicStoreTransitionEnrollmentDto): AcademicEnrollmentView?
    fun myEnrollments(request: AcademicStoreMyEnrollmentsDto): Page<AcademicEnrollmentView>
    fun myPendingEnrollmentCount(request: CountPendingEnrollmentsForStudentDto): Int
    fun listStudents(request: AcademicStoreListStudentsDto): Page<AcademicStudentSummaryView>
    fun findStudent(request: AcademicStoreFindStudentDto): AcademicStudentView?
}
