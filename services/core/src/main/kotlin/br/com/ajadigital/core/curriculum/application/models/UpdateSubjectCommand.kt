package br.com.ajadigital.core.curriculum.application.models

import br.com.ajadigital.core.identity.application.contracts.AuthenticatedActor
import br.com.ajadigital.core.shared.application.contracts.FieldChange

class UpdateSubjectCommand(
    val actor: AuthenticatedActor,
    val id: String,
    val revision: Int,
    val classId: FieldChange<String>,
    val name: FieldChange<String>,
    teacherIds: FieldChange<List<String>>,
    val color: FieldChange<String>,
    val icon: FieldChange<String>,
    val description: FieldChange<String>,
) {
    val teacherIds: FieldChange<List<String>> = when (teacherIds) {
        FieldChange.Unchanged -> FieldChange.Unchanged
        is FieldChange.Set -> FieldChange.Set(teacherIds.value.toList())
    }
}
