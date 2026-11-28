package br.com.ajadigital.email.adapter.out.health

import br.com.ajadigital.email.ports.out.HealthProbe
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
class DatabaseAndBrokerHealthProbe(
    private val dataSource: DataSource,
    private val rabbitConnectionFactory: ConnectionFactory,
) : HealthProbe {
    override fun dependenciesAvailable(): Boolean {
        val databaseAvailable = runCatching { dataSource.connection.use { it.isValid(1) } }.getOrDefault(false)
        val brokerAvailable =
            runCatching {
                rabbitConnectionFactory.createConnection().use { connection ->
                    val channel = connection.createChannel(false)
                    try {
                        channel.exchangeDeclarePassive("amq.direct")
                    } finally {
                        channel.close()
                    }
                }
                true
            }.getOrDefault(false)
        return databaseAvailable && brokerAvailable
    }
}
