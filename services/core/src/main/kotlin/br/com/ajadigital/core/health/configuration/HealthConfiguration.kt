package br.com.ajadigital.core.health.configuration

import br.com.ajadigital.core.health.domain.ports.`in`.CheckServiceHealthUseCase
import br.com.ajadigital.core.health.domain.ports.out.HealthProbe
import br.com.ajadigital.core.health.domain.usecases.CheckServiceHealth
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HealthConfiguration {
    @Bean
    fun checkServiceHealth(probe: HealthProbe): CheckServiceHealthUseCase = CheckServiceHealth(probe)
}
