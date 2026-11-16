package br.com.ajadigital.core.curriculum.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface ManageSubjectsUseCase {
    fun subjects(actor: AcademicActor, page: Int, size: Int, sort: String, classId: String?, query: String?): AcademicPage
    fun subject(actor: AcademicActor, id: String): Map<String, Any?>
    fun subjectModules(actor: AcademicActor, subjectId: String): List<Map<String, Any?>>
    fun createSubject(actor: AcademicActor, classId: String, name: String, teacherIds: List<String>, color: String, icon: String, description: String, key: String): Map<String, Any?>
    fun updateSubject(actor: AcademicActor, id: String, revision: Int, classId: String?, name: String?, teacherIds: List<String>?, color: String?, icon: String?, description: String?): Map<String, Any?>
    fun modules(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?): AcademicPage
    fun module(actor: AcademicActor, id: String): Map<String, Any?>
    fun createModule(actor: AcademicActor, subjectId: String, title: String, description: String, key: String): Map<String, Any?>
    fun updateModule(actor: AcademicActor, id: String, revision: Int, title: String?, description: String?): Map<String, Any?>
    fun reorder(actor: AcademicActor, subjectId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, pageSize: Int): Map<String, Any?>
    fun visit(actor: AcademicActor, subjectId: String, publicationIds: List<String>? = null): Map<String, Any?>
}
