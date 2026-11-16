package br.com.ajadigital.core.materials.domain.ports.`in`

import br.com.ajadigital.core.classroom.domain.models.AcademicActor
import br.com.ajadigital.core.classroom.domain.ports.out.AcademicPage
import br.com.ajadigital.core.materials.domain.models.DashboardContent
import br.com.ajadigital.core.materials.domain.models.DownloadedFile
import br.com.ajadigital.core.materials.domain.models.MaterialCreate

interface ManageContentUseCase {
    fun upload(actor: AcademicActor, key: String, name: String, mime: String, purpose: String, parentId: String, bytes: ByteArray): Map<String, Any?>
    fun provisionQuestionImage(id: String, questionId: String, name: String, mime: String, bytes: ByteArray): Map<String, Any?>
    fun file(actor: AcademicActor, id: String): Map<String, Any?>
    fun download(actor: AcademicActor, id: String, disposition: String?): DownloadedFile
    fun discard(actor: AcademicActor, id: String)
    fun materials(actor: AcademicActor, page: Int, size: Int, sort: String, moduleId: String?, subjectId: String?): AcademicPage
    fun material(actor: AcademicActor, id: String): Map<String, Any?>
    fun publish(actor: AcademicActor, key: String, moduleId: String, revision: Int, items: List<MaterialCreate>): Map<String, Any?>
    fun update(actor: AcademicActor, id: String, revision: Int, title: String?, duration: Double?, content: String?, videoUrl: String?): Map<String, Any?>
    fun reorder(actor: AcademicActor, moduleId: String, revision: Int, items: List<Pair<String, Int>>, page: Int, size: Int): Map<String, Any?>
    fun progress(actor: AcademicActor, page: Int, size: Int, sort: String, subjectId: String?, materialId: String?): Map<String, Any?>
    fun dashboard(actor: AcademicActor): DashboardContent
    fun recordProgress(actor: AcademicActor, materialId: String, completed: Boolean?): Map<String, Any?>
}
