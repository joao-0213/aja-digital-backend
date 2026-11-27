package br.com.ajadigital.core.health.configuration

import br.com.ajadigital.core.health.application.ports.`in`.CheckServiceHealthInputPort
import br.com.ajadigital.core.health.application.ports.out.HealthProbeOutputPort
import br.com.ajadigital.core.health.application.usecases.CheckServiceHealthUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HealthConfiguration {
    @Bean
    fun checkServiceHealth(probe: HealthProbeOutputPort): CheckServiceHealthInputPort = CheckServiceHealthUseCase(probe)
}
