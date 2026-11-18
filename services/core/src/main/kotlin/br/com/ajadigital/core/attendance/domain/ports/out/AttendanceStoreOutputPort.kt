package br.com.ajadigital.core.attendance.domain.ports.out
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage
import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.attendance.domain.models.AuthorizeAttendanceCreateDto
import br.com.ajadigital.core.attendance.domain.models.ListAttendanceDto
import br.com.ajadigital.core.attendance.domain.models.ListEligibleAttendanceDto
import br.com.ajadigital.core.attendance.domain.models.GetStoredAttendanceDto
import br.com.ajadigital.core.attendance.domain.models.CreateAttendanceRecordDto
import br.com.ajadigital.core.attendance.domain.models.UpdateAttendanceRecordDto



interface AttendanceStoreOutputPort {
    fun authorizeCreate(request: AuthorizeAttendanceCreateDto)
    fun list(request: ListAttendanceDto): AcademicPage
    fun eligible(request: ListEligibleAttendanceDto): AcademicPage
    fun attendance(request: GetStoredAttendanceDto): Map<String, Any?>?
    fun create(request: CreateAttendanceRecordDto): Map<String, Any?>
    fun update(request: UpdateAttendanceRecordDto): Map<String, Any?>?
}
