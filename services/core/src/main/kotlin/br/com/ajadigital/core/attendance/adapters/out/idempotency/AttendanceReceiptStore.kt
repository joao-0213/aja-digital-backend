package br.com.ajadigital.core.attendance.adapters.out.idempotency

import br.com.ajadigital.core.attendance.application.ports.out.AttendanceReceiptStoreOutputPort
import br.com.ajadigital.core.attendance.domain.models.AttendanceReceipt
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.exceptions.IdempotencyKeyReuse
import br.com.ajadigital.core.shared.domain.models.FindIdempotencyDto
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper

@Repository
class AttendanceReceiptStore(
    private val idempotency: EncodedIdempotencyStoreOutputPort,
    private val mapper: ObjectMapper,
) : AttendanceReceiptStoreOutputPort {
    override fun find(
        actorId: String,
        key: String,
        requestHash: String,
    ): AttendanceReceipt? = try {
        idempotency.findEncoded(FindIdempotencyDto(actorId, "createAttendance", "/attendance", key, requestHash))?.let { receipt(it) }
    } catch (_: IdempotencyKeyReuse) {
        throw IdempotencyKeyReuse()
    }

    override fun save(
        actorId: String,
        key: String,
        requestHash: String,
        receipt: AttendanceReceipt,
    ) {
        val stored =
            mapper.convertValue(
                mapper.valueToTree<tools.jackson.databind.JsonNode>(receipt),
                object : TypeReference<Map<String, Any?>>() {},
            )
        idempotency.saveEncoded(
            FindIdempotencyDto(actorId, "createAttendance", "/attendance", key, requestHash),
            mapper.writeValueAsString(stored),
        )
    }

    private fun receipt(value: String): AttendanceReceipt {
        val json = mapper.readTree(value)
        val records = json.path("records")
        require(records.isObject)
        return AttendanceReceipt(
            json.path("id").textValue(),
            json.path("revision").intValue(),
            json.path("createdAt").textValue(),
            json.path("updatedAt").textValue(),
            json.path("updatedBy").textValue(),
            json.path("subjectId").textValue(),
            json.path("date").textValue(),
            json.path("time").textValue(),
            records.properties().associate { (id, mark) -> id to mark.booleanValue().also { require(mark.isBoolean) } },
        )
    }
}
