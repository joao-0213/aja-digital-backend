package br.com.ajadigital.core.architecture.contractfixtures.application.contracts

data class Page<T>(val items: List<T>)
data class Envelope<T>(val value: T)
data class Item(val id: String)
data class ValidContractData(val page: Page<Envelope<Item>>)
data class ForwardingBox<T>(val value: Envelope<T>)
data class ValidForwardedContractData(val box: ForwardingBox<Item>)

interface ValidPageOutputPort {
    fun page(): Page<Envelope<Item>>
}

interface InvalidNestedOutputPort {
    fun value(): Map<String, List<Any?>>
}

interface InvalidUnboundOutputPort<T> {
    fun value(): List<T>
}

interface TransactionRunnerOutputPort {
    fun <T> run(block: () -> T): T
}
