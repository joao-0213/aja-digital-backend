package br.com.ajadigital.core.academic.application.ports.out

import br.com.ajadigital.core.academic.application.contracts.DashboardClassProjection
import br.com.ajadigital.core.academic.application.models.AcademicClassView
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentPreview
import br.com.ajadigital.core.academic.application.models.AcademicEnrollmentView
import br.com.ajadigital.core.academic.application.models.AcademicStoreChangeRoleDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateClassDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateEnrollmentDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreCreateUserDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreDashboardClassesDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreDeactivateUserDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreEnrollmentPreviewDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindClassDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindEnrollmentDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreFindStudentDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreIsCurrentAdminSessionDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreListClassesDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreListEnrollmentsDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreListStudentsDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreListUsersDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreMyEnrollmentsDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreSetClassActiveDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreTransitionEnrollmentDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreUpdateClassDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreUpdateUserDto
import br.com.ajadigital.core.academic.application.models.AcademicStoreWithdrawUserDto
import br.com.ajadigital.core.academic.application.models.AcademicStudentSummaryView
import br.com.ajadigital.core.academic.application.models.AcademicStudentView
import br.com.ajadigital.core.academic.application.models.AcademicUserView
import br.com.ajadigital.core.academic.application.models.CountPendingEnrollmentsForStudentDto
import br.com.ajadigital.core.academic.application.models.FindUserDto
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
