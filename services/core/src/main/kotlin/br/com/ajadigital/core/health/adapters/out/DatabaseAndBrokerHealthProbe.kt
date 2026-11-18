package br.com.ajadigital.core.health.adapters.out

import br.com.ajadigital.core.health.domain.ports.out.HealthProbeOutputPort
import br.com.ajadigital.core.health.domain.models.HealthProbeDto
import org.springframework.stereotype.Component
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import javax.sql.DataSource

@Component
class DatabaseAndBrokerHealthProbe(
    private val dataSource: DataSource,
    private val rabbitConnectionFactory: ConnectionFactory,
) : HealthProbeOutputPort {
    override fun dependenciesAvailable(request: HealthProbeDto): Boolean {
        val databaseAvailable = runCatching { dataSource.connection.use { it.isValid(1) } }.getOrDefault(false)
        val brokerAvailable = runCatching {
            rabbitConnectionFactory.createConnection().use { connection ->
                val channel = connection.createChannel(false)
                try { channel.exchangeDeclarePassive("amq.direct") } finally { channel.close() }
            }
            true
        }.getOrDefault(false)
        return databaseAvailable && brokerAvailable
    }
}
