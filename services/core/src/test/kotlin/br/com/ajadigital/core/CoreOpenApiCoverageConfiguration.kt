package br.com.ajadigital.core

import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration


@TestConfiguration
class CoreOpenApiCoverageConfiguration {
    @Bean
    fun coreOpenApiCoverageFilter() = CoreOpenApiCoverageFilter()
}
