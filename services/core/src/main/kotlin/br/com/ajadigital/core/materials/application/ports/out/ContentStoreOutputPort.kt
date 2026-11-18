package br.com.ajadigital.core.materials.application.ports.out
import br.com.ajadigital.core.materials.application.models.*

import br.com.ajadigital.core.materials.domain.models.*
import br.com.ajadigital.core.academic.domain.contracts.AcademicPage

interface ContentStoreOutputPort {
    fun upload(request: ContentStoreUploadDto): Map<String, Any?>
    fun provisionQuestionImage(request: ContentStoreProvisionQuestionImageDto): Map<String, Any?>
    fun file(request: ContentStoreFileDto): Map<String, Any?>?
    fun download(request: ContentStoreDownloadDto): DownloadedFile?
    fun discard(request: ContentStoreDiscardDto): Unit
    fun materials(request: ContentStoreMaterialsDto): AcademicPage
    fun material(request: ContentStoreMaterialDto): Map<String, Any?>?
    fun authorizeMaterialPublication(request: ContentStoreAuthorizeMaterialPublicationDto): Unit
    fun publishMaterials(request: ContentStorePublishMaterialsDto): AcademicPage?
    fun updateMaterial(request: ContentStoreUpdateMaterialDto): Map<String, Any?>?
    fun reorderMaterials(request: ContentStoreReorderMaterialsDto): AcademicPage?
    fun progress(request: ContentStoreProgressDto): AcademicPage
    fun dashboard(request: ContentStoreDashboardDto): DashboardContent
    fun recordProgress(request: ContentStoreRecordProgressDto): Map<String, Any?>?
}
