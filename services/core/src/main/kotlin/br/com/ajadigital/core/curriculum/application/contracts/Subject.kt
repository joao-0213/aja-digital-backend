package br.com.ajadigital.core.curriculum.application.contracts

import java.util.Collections

class Subject(
    val id: String,
    val revision: Int,
    val createdAt: String,
    val updatedAt: String,
    val updatedBy: String,
    val classId: String,
    val name: String,
    teacherIds: List<String>,
    val color: String,
    val icon: String,
    val description: String,
) {
    val teacherIds: List<String> = Collections.unmodifiableList(teacherIds.toList())

    internal fun copy(
        revision: Int = this.revision,
        teacherIds: List<String> = this.teacherIds,
    ): Subject = Subject(id, revision, createdAt, updatedAt, updatedBy, classId, name, teacherIds, color, icon, description)
}
