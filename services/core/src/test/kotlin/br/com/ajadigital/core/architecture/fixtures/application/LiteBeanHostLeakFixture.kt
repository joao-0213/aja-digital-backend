package br.com.ajadigital.core.architecture.fixtures.application

import org.springframework.context.annotation.Bean

class LiteBeanHostLeakFixture {
    @Bean
    fun bean(): String = "fixture"
}
