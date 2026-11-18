package br.com.ajadigital.core.curriculum.application.ports.`in`

import br.com.ajadigital.core.curriculum.application.models.GetModuleQuery
import br.com.ajadigital.core.curriculum.application.models.Module

interface GetModuleInputPort {
    fun module(request: GetModuleQuery): Module
}
