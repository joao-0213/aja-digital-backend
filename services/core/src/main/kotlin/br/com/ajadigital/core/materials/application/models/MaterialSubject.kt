package br.com.ajadigital.core.materials.application.models

import java.util.Collections

class MaterialSubject(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String,
    val classId: String,
    val name: String,
    val color: String,
    val icon: String,
    val description: String,
    val className: String,
    teachers: List<NamedPerson>,
    val canManageContent: Boolean,
    val hasUpdates: Boolean,
    publicationIds: List<String>,
) {
    val teachers: List<NamedPerson> = Collections.unmodifiableList(teachers.toList())
    val publicationIds: List<String> = Collections.unmodifiableList(publicationIds.toList())
}
