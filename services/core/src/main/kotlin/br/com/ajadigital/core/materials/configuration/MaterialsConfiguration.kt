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
import br.com.ajadigital.core.materials.application.ports.out.FileStoreOutputPort
import br.com.ajadigital.core.materials.application.ports.out.QuestionImageOutputPort
import br.com.ajadigital.core.materials.application.ports.out.QuestionServiceAccessOutputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialReadOutputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialWriteOutputPort
import br.com.ajadigital.core.materials.application.ports.out.LearningProgressOutputPort
import br.com.ajadigital.core.materials.application.ports.out.MaterialPublicationReceiptOutputPort
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
import br.com.ajadigital.core.shared.application.ports.out.EncodedIdempotencyStoreOutputPort
import br.com.ajadigital.core.shared.domain.ports.out.TransactionRunnerOutputPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MaterialsConfiguration {
    @Bean
    fun uploadFile(store: FileStoreOutputPort): UploadFileInputPort = UploadFileUseCase(store)

    @Bean
    fun provisionQuestionImage(store: QuestionImageOutputPort, serviceAccess: QuestionServiceAccessOutputPort): ProvisionQuestionImageInputPort =
        ProvisionQuestionImageUseCase(store, serviceAccess)

    @Bean
    fun getFileMetadata(store: FileStoreOutputPort): GetFileMetadataInputPort = GetFileMetadataUseCase(store)

    @Bean
    fun downloadFile(store: FileStoreOutputPort): DownloadFileInputPort = DownloadFileUseCase(store)

    @Bean
    fun discardFile(store: FileStoreOutputPort): DiscardFileInputPort = DiscardFileUseCase(store)

    @Bean
    fun listMaterials(store: MaterialReadOutputPort): ListMaterialsInputPort = ListMaterialsUseCase(store)

    @Bean
    fun getMaterial(store: MaterialReadOutputPort): GetMaterialInputPort = GetMaterialUseCase(store)

    @Bean
    fun publishMaterials(
        store: MaterialWriteOutputPort,
        idempotency: EncodedIdempotencyStoreOutputPort,
        codec: MaterialPublicationReceiptOutputPort,
        transactionRunner: TransactionRunnerOutputPort,
    ): PublishMaterialsInputPort = PublishMaterialsUseCase(store, idempotency, codec, transactionRunner)

    @Bean
    fun updateMaterial(store: MaterialWriteOutputPort): UpdateMaterialInputPort = UpdateMaterialUseCase(store)

    @Bean
    fun reorderMaterials(store: MaterialWriteOutputPort): ReorderMaterialsInputPort = ReorderMaterialsUseCase(store)

    @Bean
    fun getLearningProgress(store: LearningProgressOutputPort): GetLearningProgressInputPort = GetLearningProgressUseCase(store)

    @Bean
    fun getDashboardContent(store: LearningProgressOutputPort): GetDashboardContentInputPort = GetDashboardContentUseCase(store)

    @Bean
    fun recordMaterialProgress(store: LearningProgressOutputPort): RecordMaterialProgressInputPort = RecordMaterialProgressUseCase(store)
}
