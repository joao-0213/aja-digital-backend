package br.com.ajadigital.core.materials

import br.com.ajadigital.core.materials.application.models.DownloadedFile
import br.com.ajadigital.core.materials.application.models.ProvisionQuestionImageCommand
import br.com.ajadigital.core.materials.application.models.QuestionServiceCredential
import br.com.ajadigital.core.materials.application.ports.`in`.ProvisionQuestionImageInputPort
import br.com.ajadigital.core.materials.application.ports.out.QuestionImageOutputPort
import br.com.ajadigital.core.materials.application.usecases.ProvisionQuestionImageUseCase
import br.com.ajadigital.core.materials.adapters.out.security.QuestionServiceAccess
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MaterialsContractSafetyTests {
    @Test
    fun `private provisioning rejects a direct call without a service credential`() {
        val store = QuestionImageOutputPort {
            error("unauthorized call reached storage")
        }
        val port: ProvisionQuestionImageInputPort = ProvisionQuestionImageUseCase(store, QuestionServiceAccess("synthetic-private-test"))
        val pngHeader = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        assertThrows(SecurityException::class.java) {
            port.provisionQuestionImage(
                ProvisionQuestionImageCommand("file", "question", "image.png", "image/png", pngHeader, QuestionServiceCredential(null)),
            )
        }
    }

    @Test
    fun `upload and downloaded bytes cannot be changed through either array reference`() {
        val input = byteArrayOf(1, 2, 3)
        val provision = ProvisionQuestionImageCommand("file", "question", "image.png", "image/png", input, QuestionServiceCredential(null))
        val download = DownloadedFile("image.png", "image/png", input, false)
        input[0] = 9
        provision.content.copy()[1] = 8
        download.bytes[2] = 7
        assertArrayEquals(byteArrayOf(1, 2, 3), provision.content.copy())
        assertArrayEquals(byteArrayOf(1, 2, 3), download.bytes)
    }
}
