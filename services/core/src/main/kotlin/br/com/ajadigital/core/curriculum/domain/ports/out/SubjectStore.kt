package br.com.ajadigital.core.curriculum.domain.ports.out
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage

interface SubjectStore {
    fun authorize(actorId: String, role: String, sessionHash: String, subjectId: String? = null)
    fun subjects(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, classId: String?, query: String?): AcademicPage
    fun subject(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>?
    fun subjectModules(subjectId: String, actorId: String, role: String, sessionHash: String): List<Map<String, Any?>>
    fun createSubject(actorId: String, sessionHash: String, classId: String, name: String, teacherIds: List<String>, color: String, icon: String, description: String): Map<String, Any?>
    fun updateSubject(id: String, actorId: String, sessionHash: String, expectedRevision: Int, classId: String?, name: String?, teacherIds: List<String>?, color: String?, icon: String?, description: String?): Map<String, Any?>?
    fun modules(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, subjectId: String?): AcademicPage
    fun module(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>?
    fun createModule(actorId: String, role: String, sessionHash: String, subjectId: String, title: String, description: String): Map<String, Any?>
    fun updateModule(id: String, actorId: String, role: String, sessionHash: String, expectedRevision: Int, title: String?, description: String?): Map<String, Any?>?
    fun reorderModules(subjectId: String, actorId: String, role: String, sessionHash: String, expectedRevision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): AcademicPage?
    fun visit(subjectId: String, actorId: String, role: String, sessionHash: String, publicationIds: List<String>?): Map<String, Any?>?
}
