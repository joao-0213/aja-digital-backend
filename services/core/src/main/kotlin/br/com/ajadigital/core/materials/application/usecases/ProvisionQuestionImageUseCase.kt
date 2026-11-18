package br.com.ajadigital.core.materials.application.usecases

import br.com.ajadigital.core.materials.application.models.FileMetadata
import br.com.ajadigital.core.materials.application.models.ProvisionQuestionImageCommand
import br.com.ajadigital.core.materials.application.models.StoreQuestionImageCommand
import br.com.ajadigital.core.materials.application.ports.`in`.ProvisionQuestionImageInputPort
import br.com.ajadigital.core.materials.application.ports.out.QuestionImageOutputPort
import br.com.ajadigital.core.materials.application.ports.out.QuestionServiceAccessOutputPort
import br.com.ajadigital.core.materials.domain.exceptions.InvalidQuestionServiceCredential
import br.com.ajadigital.core.materials.domain.policies.FileRules
import br.com.ajadigital.core.materials.domain.policies.MaterialRules

class ProvisionQuestionImageUseCase(private val store: QuestionImageOutputPort, private val serviceAccess: QuestionServiceAccessOutputPort) : ProvisionQuestionImageInputPort {
    override fun provisionQuestionImage(request: ProvisionQuestionImageCommand): FileMetadata {
        if (!serviceAccess.allows(request.credential)) throw InvalidQuestionServiceCredential()
        val id = request.id
        val questionId = request.questionId
        val name = request.name
        require(MaterialRules.validId(id) && MaterialRules.validId(questionId) && FileRules.validFilename(name))
        val mime = FileRules.normalizedMime(request.mime)
        val content = request.content
        require(content.size in 1..(5 * 1024 * 1024) && mime in setOf("image/png", "image/jpeg", "image/webp") && FileRules.validFileSignature(name, mime, content.header()))
        return store.provisionQuestionImage(StoreQuestionImageCommand(id, questionId, name, mime, content))
    }
}
