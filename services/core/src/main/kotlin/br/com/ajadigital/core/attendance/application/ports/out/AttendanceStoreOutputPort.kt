package br.com.ajadigital.core.attendance.application.ports.out
import br.com.ajadigital.core.attendance.domain.models.AttendanceDraft
import br.com.ajadigital.core.attendance.application.models.AttendanceCreateAuthorizationCommand
import br.com.ajadigital.core.attendance.application.models.ListAttendanceSessionsRecordQuery
import br.com.ajadigital.core.attendance.application.models.ListEligibleAttendanceStudentsRecordQuery
import br.com.ajadigital.core.attendance.application.models.GetAttendanceSessionRecordQuery
import br.com.ajadigital.core.attendance.application.models.AttendanceRecordCommand
import br.com.ajadigital.core.attendance.application.models.UpdateAttendanceRecordCommand
import br.com.ajadigital.core.attendance.domain.models.AttendanceSessionRecord
import br.com.ajadigital.core.attendance.domain.models.AttendanceSessionPageRecord
import br.com.ajadigital.core.attendance.domain.models.EligibleAttendanceStudentRecord
import br.com.ajadigital.core.attendance.domain.models.EligibleAttendanceStudentPageRecord



interface AttendanceStoreOutputPort {
    fun authorizeCreate(request: AttendanceCreateAuthorizationCommand)
    fun list(request: ListAttendanceSessionsRecordQuery): AttendanceSessionPageRecord
    fun eligible(request: ListEligibleAttendanceStudentsRecordQuery): EligibleAttendanceStudentPageRecord
    fun attendance(request: GetAttendanceSessionRecordQuery): AttendanceSessionRecord?
    fun create(request: AttendanceRecordCommand): AttendanceSessionRecord
    fun update(request: UpdateAttendanceRecordCommand): AttendanceSessionRecord?
}
