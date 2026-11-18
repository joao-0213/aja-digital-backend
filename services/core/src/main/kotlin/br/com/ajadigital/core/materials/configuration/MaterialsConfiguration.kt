package br.com.ajadigital.core.materials.configuration

import br.com.ajadigital.core.materials.application.ports.`in`.DiscardFileInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.DownloadFileInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetDashboardContentInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetFileMetadataInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetLearningProgressInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.GetMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.ListMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.ProvisionQuestionImageInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.PublishMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.RecordMaterialProgressInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.ReorderMaterialsInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.UpdateMaterialInputPort
import br.com.ajadigital.core.materials.application.ports.`in`.UploadFileInputPort
import br.com.ajadigital.core.materials.application.ports.out.ContentStoreOutputPort
import br.com.ajadigital.core.materials.application.usecases.DiscardFileUseCase
import br.com.ajadigital.core.materials.application.usecases.DownloadFileUseCase
import br.com.ajadigital.core.materials.application.usecases.GetDashboardContentUseCase
import br.com.ajadigital.core.materials.application.usecases.GetFileMetadataUseCase
import br.com.ajadigital.core.materials.application.usecases.GetLearningProgressUseCase
import br.com.ajadigital.core.materials.application.usecases.GetMaterialUseCase
import br.com.ajadigital.core.materials.application.usecases.ListMaterialsUseCase
import br.com.ajadigital.core.materials.application.usecases.ProvisionQuestionImageUseCase
import br.com.ajadigital.core.materials.application.usecases.PublishMaterialsUseCase
import br.com.ajadigital.core.materials.application.usecases.RecordMaterialProgressUseCase
import br.com.ajadigital.core.materials.application.usecases.ReorderMaterialsUseCase
import br.com.ajadigital.core.materials.application.usecases.UpdateMaterialUseCase
import br.com.ajadigital.core.materials.application.usecases.UploadFileUseCase
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import br.com.ajadigital.core.shared.domain.ports.out.JsonSerializerOutputPort

@Configuration
class MaterialsConfiguration {
    @Bean
    fun uploadFile(store: ContentStoreOutputPort): UploadFileInputPort = UploadFileUseCase(store)

    @Bean
    fun provisionQuestionImage(store: ContentStoreOutputPort): ProvisionQuestionImageInputPort = ProvisionQuestionImageUseCase(store)

    @Bean
    fun getFileMetadata(store: ContentStoreOutputPort): GetFileMetadataInputPort = GetFileMetadataUseCase(store)

    @Bean
    fun downloadFile(store: ContentStoreOutputPort): DownloadFileInputPort = DownloadFileUseCase(store)

    @Bean
    fun discardFile(store: ContentStoreOutputPort): DiscardFileInputPort = DiscardFileUseCase(store)

    @Bean
    fun listMaterials(store: ContentStoreOutputPort): ListMaterialsInputPort = ListMaterialsUseCase(store)

    @Bean
    fun getMaterial(store: ContentStoreOutputPort): GetMaterialInputPort = GetMaterialUseCase(store)

    @Bean
    fun publishMaterials(
        store: ContentStoreOutputPort,
        idempotency: IdempotencyStoreOutputPort,
        mapper: JsonSerializerOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): PublishMaterialsInputPort = PublishMaterialsUseCase(store, idempotency, mapper, transactionRunner)

    @Bean
    fun updateMaterial(store: ContentStoreOutputPort): UpdateMaterialInputPort = UpdateMaterialUseCase(store)

    @Bean
    fun reorderMaterials(store: ContentStoreOutputPort): ReorderMaterialsInputPort = ReorderMaterialsUseCase(store)

    @Bean
    fun getLearningProgress(store: ContentStoreOutputPort): GetLearningProgressInputPort = GetLearningProgressUseCase(store)

    @Bean
    fun getDashboardContent(store: ContentStoreOutputPort): GetDashboardContentInputPort = GetDashboardContentUseCase(store)

    @Bean
    fun recordMaterialProgress(store: ContentStoreOutputPort): RecordMaterialProgressInputPort = RecordMaterialProgressUseCase(store)
}
