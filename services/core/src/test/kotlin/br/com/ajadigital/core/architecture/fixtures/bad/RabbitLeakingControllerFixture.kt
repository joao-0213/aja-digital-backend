package br.com.ajadigital.core.architecture.fixtures.adapters.`in`.controllers

class RabbitLeakingController(private val factory: com.rabbitmq.client.ConnectionFactory)

class SpringRabbitLeakingController(private val factory: org.springframework.amqp.rabbit.connection.ConnectionFactory)
