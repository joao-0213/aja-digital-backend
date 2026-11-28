package br.com.ajadigital.core.attendance.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import java.util.Collections

class UpdateAttendanceCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val revision: Int,
    records: Map<String, Boolean>,
) {
    val records: Map<String, Boolean> = Collections.unmodifiableMap(LinkedHashMap(records))
}
