package br.com.ajadigital.core.materials.configuration

import br.com.ajadigital.core.materials.domain.ports.`in`.DiscardFileUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.DownloadFileUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.GetDashboardContentUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.GetFileMetadataUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.GetLearningProgressUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.GetMaterialUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.ListMaterialsUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.ProvisionQuestionImageUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.PublishMaterialsUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.RecordMaterialProgressUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.ReorderMaterialsUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.UpdateMaterialUseCase
import br.com.ajadigital.core.materials.domain.ports.`in`.UploadFileUseCase
import br.com.ajadigital.core.materials.domain.ports.out.ContentStore
import br.com.ajadigital.core.materials.domain.usecases.DiscardFile
import br.com.ajadigital.core.materials.domain.usecases.DownloadFile
import br.com.ajadigital.core.materials.domain.usecases.GetDashboardContent
import br.com.ajadigital.core.materials.domain.usecases.GetFileMetadata
import br.com.ajadigital.core.materials.domain.usecases.GetLearningProgress
import br.com.ajadigital.core.materials.domain.usecases.GetMaterial
import br.com.ajadigital.core.materials.domain.usecases.ListMaterials
import br.com.ajadigital.core.materials.domain.usecases.ProvisionQuestionImage
import br.com.ajadigital.core.materials.domain.usecases.PublishMaterials
import br.com.ajadigital.core.materials.domain.usecases.RecordMaterialProgress
import br.com.ajadigital.core.materials.domain.usecases.ReorderMaterials
import br.com.ajadigital.core.materials.domain.usecases.UpdateMaterial
import br.com.ajadigital.core.materials.domain.usecases.UploadFile
import br.com.ajadigital.core.shared.domain.ports.out.IdempotencyStore
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper

@Configuration
class MaterialsConfiguration {
    @Bean
    fun uploadFile(store: ContentStore): UploadFileUseCase = UploadFile(store)

    @Bean
    fun provisionQuestionImage(store: ContentStore): ProvisionQuestionImageUseCase = ProvisionQuestionImage(store)

    @Bean
    fun getFileMetadata(store: ContentStore): GetFileMetadataUseCase = GetFileMetadata(store)

    @Bean
    fun downloadFile(store: ContentStore): DownloadFileUseCase = DownloadFile(store)

    @Bean
    fun discardFile(store: ContentStore): DiscardFileUseCase = DiscardFile(store)

    @Bean
    fun listMaterials(store: ContentStore): ListMaterialsUseCase = ListMaterials(store)

    @Bean
    fun getMaterial(store: ContentStore): GetMaterialUseCase = GetMaterial(store)

    @Bean
    fun publishMaterials(
        store: ContentStore,
        idempotency: IdempotencyStore,
        mapper: ObjectMapper,
        transactionRunner: TransactionRunner,
    ): PublishMaterialsUseCase = PublishMaterials(store, idempotency, mapper, transactionRunner)

    @Bean
    fun updateMaterial(store: ContentStore): UpdateMaterialUseCase = UpdateMaterial(store)

    @Bean
    fun reorderMaterials(store: ContentStore): ReorderMaterialsUseCase = ReorderMaterials(store)

    @Bean
    fun getLearningProgress(store: ContentStore): GetLearningProgressUseCase = GetLearningProgress(store)

    @Bean
    fun getDashboardContent(store: ContentStore): GetDashboardContentUseCase = GetDashboardContent(store)

    @Bean
    fun recordMaterialProgress(store: ContentStore): RecordMaterialProgressUseCase = RecordMaterialProgress(store)
}
