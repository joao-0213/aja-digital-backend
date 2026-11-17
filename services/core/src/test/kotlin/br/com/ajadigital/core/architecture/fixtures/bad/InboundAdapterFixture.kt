package br.com.ajadigital.core.architecture.fixtures.adapters.`in`.controllers

class InboundAdapterFixture(
    private val useCase: br.com.ajadigital.core.architecture.fixtures.domain.usecases.ConcreteFixtureUseCase,
    private val adapter: br.com.ajadigital.core.architecture.fixtures.adapters.out.OutboundAdapterFixture,
)
