package br.com.ajadigital.core.curriculum.application.ports.out

import br.com.ajadigital.core.curriculum.application.contracts.Subject
import br.com.ajadigital.core.curriculum.application.models.CreateModuleCommand
import br.com.ajadigital.core.curriculum.application.models.CreateSubjectCommand
import br.com.ajadigital.core.curriculum.application.models.Module


interface CurriculumReceiptCodecOutputPort {
    fun fingerprint(request: CreateSubjectCommand): String
    fun fingerprint(request: CreateModuleCommand): String
    fun encode(subject: Subject): String
    fun encode(module: Module): String
    fun decodeSubject(response: String): Subject
    fun decodeModule(response: String): Module
}
