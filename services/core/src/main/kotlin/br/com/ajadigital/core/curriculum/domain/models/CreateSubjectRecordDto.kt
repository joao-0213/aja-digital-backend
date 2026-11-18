package br.com.ajadigital.core.curriculum.domain.models

data class CreateSubjectRecordDto(val actorId: String, val sessionHash: String, val classId: String, val name: String, val teacherIds: List<String>, val color: String, val icon: String, val description: String)
