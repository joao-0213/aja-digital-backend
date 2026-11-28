package br.com.ajadigital.core.attendance.application.ports.out

import br.com.ajadigital.core.attendance.domain.models.AttendanceReceipt

interface AttendanceReceiptStoreOutputPort {
    fun find(
        actorId: String,
        key: String,
        requestHash: String,
    ): AttendanceReceipt?

    fun save(
        actorId: String,
        key: String,
        requestHash: String,
        receipt: AttendanceReceipt,
    )
}
