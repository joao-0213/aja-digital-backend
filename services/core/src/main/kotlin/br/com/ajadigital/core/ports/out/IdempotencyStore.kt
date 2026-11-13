package br.com.ajadigital.core.ports.out



interface IdempotencyStore {
    fun find(actorId: String, operation: String, target: String, key: String, requestHash: String): Map<String, Any?>?
    fun save(actorId: String, operation: String, target: String, key: String, requestHash: String, response: Map<String, Any?>)
}
