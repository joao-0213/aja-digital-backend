package br.com.ajadigital.core.curriculum.domain.models

data class UpdateSubjectRecordDto(val id: String, val actorId: String, val sessionHash: String, val expectedRevision: Int, val classId: String?, val name: String?, val teacherIds: List<String>?, val color: String?, val icon: String?, val description: String?)
