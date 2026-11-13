package br.com.ajadigital.core

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration


@TestConfiguration
class MutableTestClockConfiguration {
    @Bean
    @Primary
    fun mutableTestClock() = MutableTestClock()
}
