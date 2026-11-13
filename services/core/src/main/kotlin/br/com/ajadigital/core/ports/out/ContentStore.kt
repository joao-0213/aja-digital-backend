package br.com.ajadigital.core.ports.out

import br.com.ajadigital.core.ports.out.AcademicPage


interface ContentStore {
    fun upload(actorId: String, role: String, sessionHash: String, key: String, name: String, declaredMime: String, purpose: String, parentId: String, bytes: ByteArray): Map<String, Any?>
    fun provisionQuestionImage(id: String, questionId: String, name: String, mime: String, bytes: ByteArray): Map<String, Any?>
    fun file(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>?
    fun download(id: String, actorId: String, role: String, sessionHash: String, disposition: String?): DownloadedFile?
    fun discard(id: String, actorId: String, role: String, sessionHash: String)
    fun materials(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, moduleId: String?, subjectId: String?): AcademicPage
    fun material(id: String, actorId: String, role: String, sessionHash: String): Map<String, Any?>?
    fun authorizeMaterialPublication(actorId: String, role: String, sessionHash: String, moduleId: String)
    fun publishMaterials(actorId: String, role: String, sessionHash: String, moduleId: String, expectedRevision: Int, items: List<MaterialCreate>): AcademicPage?
    fun updateMaterial(actorId: String, role: String, sessionHash: String, id: String, expectedRevision: Int, title: String?, duration: Double?, content: String?, videoUrl: String?): Map<String, Any?>?
    fun reorderMaterials(actorId: String, role: String, sessionHash: String, moduleId: String, expectedRevision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): AcademicPage?
    fun progress(actorId: String, role: String, sessionHash: String, page: Int, size: Int, sort: String, subjectId: String?, materialId: String?): AcademicPage
    fun dashboard(actorId: String, role: String, sessionHash: String, progressLimit: Int): DashboardContent
    fun recordProgress(actorId: String, role: String, sessionHash: String, materialId: String, completed: Boolean): Map<String, Any?>?
}
