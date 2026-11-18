package br.com.ajadigital.core.materials.adapters.`in`.controllers.models.responses

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.Material
import br.com.ajadigital.core.materials.application.models.MaterialDetail
import br.com.ajadigital.core.materials.application.models.MaterialSubject
import br.com.ajadigital.core.shared.application.contracts.Page

internal fun <T, R> Page<T>.toResponse(transform: (T) -> R) =
    mapOf("items" to items.map(transform), "page" to page, "pageSize" to pageSize, "total" to total)

internal fun FileMetadata.toResponse() = mapOf(
    "id" to id, "name" to name, "size" to size, "mime" to mime, "purpose" to purpose.publicValue,
    "parentId" to parentId, "status" to status.value, "downloadUrl" to "/api/v1/files/$id/content",
)

internal fun Material.toResponse() = mapOf(
    "id" to id, "revision" to revision, "createdAt" to createdAt, "updatedAt" to updatedAt, "updatedBy" to updatedBy,
    "moduleId" to moduleId, "title" to title, "kind" to kind.value, "duration" to duration, "content" to content,
    "fileId" to fileId, "videoUrl" to videoUrl, "file" to file?.toResponse(), "author" to author, "order" to order,
)

internal fun MaterialDetail.toResponse() = mapOf(
    "material" to material.toResponse(), "module" to module, "subject" to subject.toResponse(), "canManageContent" to canManageContent,
)

private fun MaterialSubject.toResponse() = mapOf(
    "id" to id, "revision" to revision, "createdAt" to createdAt, "updatedAt" to updatedAt, "updatedBy" to updatedBy,
    "classId" to classId, "name" to name, "teacherIds" to teachers.map { it.id }, "color" to color, "icon" to icon,
    "description" to description, "className" to className, "teachers" to teachers, "canManageContent" to canManageContent,
    "hasUpdates" to hasUpdates, "publicationIds" to publicationIds,
)
