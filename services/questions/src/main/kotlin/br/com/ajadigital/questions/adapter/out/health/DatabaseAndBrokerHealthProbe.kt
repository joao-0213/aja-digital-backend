package br.com.ajadigital.questions.adapter.out.health

import br.com.ajadigital.questions.ports.out.HealthProbe
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
class DatabaseAndBrokerHealthProbe(
    private val dataSource: DataSource,
) : HealthProbe {
    override fun dependenciesAvailable(): Boolean = runCatching { dataSource.connection.use { it.isValid(1) } }.getOrDefault(false)
}
