package br.com.ajadigital.core.attendance

import br.com.ajadigital.core.attendance.adapters.out.idempotency.AttendanceReceiptStore
import br.com.ajadigital.core.attendance.domain.models.AttendanceReceipt
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class AttendanceReceiptStoreTests {
    @Test
    fun `receipt port stores and restores the exact staff fields and idempotency identity`() {
        var saved: Pair<FindIdempotencyDto, String>? = null
        val shared = object : EncodedIdempotencyStoreOutputPort {
            override fun findEncoded(request: FindIdempotencyDto): String? {
                assertEquals("createAttendance", request.operation)
                assertEquals("/attendance", request.target)
                assertEquals("attendance-key", request.key)
                assertEquals("receipt-hash", request.requestHash)
                return saved?.second
            }
            override fun saveEncoded(request: FindIdempotencyDto, response: String) { saved = request to response }
        }
        val port = AttendanceReceiptStore(shared, JsonMapper.builder().build())
        val receipt = AttendanceReceipt("att-1", 1, "2026-11-12T00:00:00Z", "2026-11-12T00:00:00Z", "teacher", "subject-1", "2026-11-12", "14:00", mapOf("student-1" to true, "student-2" to false))
        port.save("teacher", "attendance-key", "receipt-hash", receipt)
        assertEquals(receipt.id, saved?.let { port.find(it.first.actorId, it.first.key, it.first.requestHash) }?.id)
        assertEquals(receipt.records, port.find("teacher", "attendance-key", "receipt-hash")?.records)
    }
}
